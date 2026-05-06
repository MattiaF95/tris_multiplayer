package it.gol8.secure.service;

import it.gol8.secure.model.GameInvite;
import it.gol8.secure.model.GameStatus;
import it.gol8.secure.model.InviteStatus;
import it.gol8.secure.model.MultiplayerGame;
import it.gol8.secure.model.SessionUser;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MultiplayerTicTacToeService {

    private static final long ACTIVE_USER_WINDOW_MS = 5 * 60 * 1000;
    private static final long INVITE_TTL_MS = 10 * 60 * 1000;
    private static final int COOKIE_AGE_SECONDS = 60 * 60 * 24 * 30;

    private static final String COOKIE_USERNAME = "mp_username";
    private static final String COOKIE_LAST_GAME = "mp_last_game";

    private final ConcurrentHashMap<String, SessionUser> usersBySession = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, GameInvite> invites = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, MultiplayerGame> games = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionToGame = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SessionScore> scores = new ConcurrentHashMap<>();

    public void populateDashboard(Model model, String username, HttpSession session, HttpServletResponse response) {
        String sessionId = session.getId();
        long now = System.currentTimeMillis();

        cleanupStale(now);
        registerSessionUser(sessionId, username, now);
        writeCookie(response, COOKIE_USERNAME, username);

        String currentGameId = normalizeCurrentGame(sessionId);
        if (currentGameId != null) {
            writeCookie(response, COOKIE_LAST_GAME, currentGameId);
        }

        SessionScore score = scores.computeIfAbsent(sessionId, k -> new SessionScore());

        model.addAttribute("activeUsers", listActiveUsers(sessionId, now));
        model.addAttribute("incomingInvites", listIncomingInvites(sessionId));
        model.addAttribute("outgoingInvites", listOutgoingInvites(sessionId));
        model.addAttribute("currentGameId", currentGameId);
        model.addAttribute("scoreWins", score.wins.get());
        model.addAttribute("scoreLosses", score.losses.get());
        model.addAttribute("scoreDraws", score.draws.get());
        model.addAttribute("scorePlayed", score.wins.get() + score.losses.get() + score.draws.get());

        GameSnapshot snapshot = gameSnapshotFor(sessionId);
        model.addAttribute("board", snapshot.board);
        model.addAttribute("gameStatus", snapshot.status);
        model.addAttribute("resultMessage", snapshot.message);
        model.addAttribute("turnText", snapshot.turnText);
        model.addAttribute("mySymbol", snapshot.mySymbol);
        model.addAttribute("opponent", snapshot.opponent);
        model.addAttribute("canPlay", snapshot.canPlay);
        model.addAttribute("starter", snapshot.starter);
        model.addAttribute("starterSymbol", snapshot.starterSymbol);
    }

    public String sendInvite(HttpSession session, String username, String invitedSessionId) {
        String mySessionId = session.getId();
        long now = System.currentTimeMillis();

        registerSessionUser(mySessionId, username, now);
        cleanupStale(now);

        if (mySessionId.equals(invitedSessionId)) {
            return "Non puoi invitare te stesso.";
        }

        SessionUser invited = usersBySession.get(invitedSessionId);
        if (invited == null) {
            return "Utente non disponibile.";
        }

        if (sessionToGame.containsKey(mySessionId) || sessionToGame.containsKey(invitedSessionId)) {
            return "Uno dei due utenti e gia in partita.";
        }

        for (GameInvite invite : invites.values()) {
            if (invite.getStatus() != InviteStatus.PENDING) {
                continue;
            }
            boolean samePair = (invite.getInviterSessionId().equals(mySessionId)
                    && invite.getInvitedSessionId().equals(invitedSessionId))
                    || (invite.getInviterSessionId().equals(invitedSessionId)
                    && invite.getInvitedSessionId().equals(mySessionId));
            if (samePair) {
                return "Esiste gia un invito pendente tra questi utenti.";
            }
        }

        GameInvite invite = new GameInvite(
                UUID.randomUUID().toString(),
                mySessionId,
                username,
                invitedSessionId,
                invited.getUsername(),
                InviteStatus.PENDING,
                now
        );
        invites.put(invite.getInviteId(), invite);

        return "Invito inviato a " + invited.getUsername() + ".";
    }

    public String respondInvite(HttpSession session, String inviteId, boolean accept, HttpServletResponse response) {
        String mySessionId = session.getId();
        GameInvite invite = invites.get(inviteId);
        if (invite == null) {
            return "Invito non trovato.";
        }

        synchronized (invite) {
            if (!invite.getInvitedSessionId().equals(mySessionId)) {
                return "Non puoi gestire questo invito.";
            }

            if (invite.getStatus() != InviteStatus.PENDING) {
                return "Invito gia gestito.";
            }

            if (!accept) {
                invite.setStatus(InviteStatus.REJECTED);
                return "Invito rifiutato.";
            }

            if (sessionToGame.containsKey(invite.getInviterSessionId()) || sessionToGame.containsKey(invite.getInvitedSessionId())) {
                invite.setStatus(InviteStatus.REJECTED);
                return "Invito annullato: uno dei giocatori e gia in partita.";
            }

            String gameId = createGame(invite);
            invite.setStatus(InviteStatus.ACCEPTED);
            writeCookie(response, COOKIE_LAST_GAME, gameId);
            return "Invito accettato. Partita creata.";
        }
    }

    public void playMove(HttpSession session, int index) {
        String sessionId = session.getId();
        String gameId = sessionToGame.get(sessionId);
        if (gameId == null) {
            return;
        }

        MultiplayerGame game = games.get(gameId);
        if (game == null) {
            sessionToGame.remove(sessionId);
            return;
        }

        if (index < 0 || index > 8) {
            game.setMessage("Mossa non valida.");
            game.setUpdatedAtEpochMs(System.currentTimeMillis());
            return;
        }

        game.getLock().lock();
        try {
            if (game.getStatus() != GameStatus.IN_PROGRESS) {
                game.setMessage("La partita e gia terminata.");
                game.setUpdatedAtEpochMs(System.currentTimeMillis());
                return;
            }

            if (!sessionId.equals(game.getTurnSessionId())) {
                game.setMessage("Attendi il tuo turno.");
                game.setUpdatedAtEpochMs(System.currentTimeMillis());
                return;
            }

            char symbol = game.getSymbolFor(sessionId);
            if (symbol == '-') {
                return;
            }

            if (game.getBoard()[index] != '-') {
                game.setMessage("Casella occupata, scegli una casella libera.");
                game.setUpdatedAtEpochMs(System.currentTimeMillis());
                return;
            }

            game.getBoard()[index] = symbol;

            if (isWinner(game.getBoard(), symbol)) {
                if (symbol == 'X') {
                    game.setStatus(GameStatus.X_WON);
                } else {
                    game.setStatus(GameStatus.O_WON);
                }
                game.setWinnerSessionId(sessionId);
                game.setMessage("Partita terminata: " + usernameOf(game, sessionId) + " ha vinto.");
                updateScoresOnWin(game, sessionId);
                game.setUpdatedAtEpochMs(System.currentTimeMillis());
                return;
            }

            if (isBoardFull(game.getBoard())) {
                game.setStatus(GameStatus.DRAW);
                game.setMessage("Partita terminata: pareggio.");
                updateScoresOnDraw(game);
                game.setUpdatedAtEpochMs(System.currentTimeMillis());
                return;
            }

            String nextTurn = sessionId.equals(game.getPlayerXSessionId())
                    ? game.getPlayerOSessionId()
                    : game.getPlayerXSessionId();
            game.setTurnSessionId(nextTurn);
            game.setMessage("Mossa registrata. Tocca a " + usernameOf(game, nextTurn) + ".");
            game.setUpdatedAtEpochMs(System.currentTimeMillis());
        } finally {
            game.getLock().unlock();
        }
    }

    public void leaveGame(HttpSession session) {
        String sessionId = session.getId();
        String gameId = sessionToGame.get(sessionId);
        if (gameId == null) {
            return;
        }

        MultiplayerGame game = games.remove(gameId);
        if (game == null) {
            sessionToGame.remove(sessionId);
            return;
        }

        sessionToGame.remove(game.getPlayerXSessionId());
        sessionToGame.remove(game.getPlayerOSessionId());
    }

    public void restartCurrentGame(HttpSession session) {
        String sessionId = session.getId();
        String gameId = sessionToGame.get(sessionId);
        if (gameId == null) {
            return;
        }

        MultiplayerGame game = games.get(gameId);
        if (game == null) {
            sessionToGame.remove(sessionId);
            return;
        }

        game.getLock().lock();
        try {
            Arrays.fill(game.getBoard(), '-');
            game.setStatus(GameStatus.IN_PROGRESS);
            game.setWinnerSessionId(null);

            String nextStarter = game.getRoundStarterSessionId().equals(game.getPlayerXSessionId())
                    ? game.getPlayerOSessionId()
                    : game.getPlayerXSessionId();
            game.setRoundStarterSessionId(nextStarter);
            game.setTurnSessionId(nextStarter);

            char starterSymbol = game.getSymbolFor(nextStarter);
            game.setMessage("Nuova partita avviata. Inizia " + usernameOf(game, nextStarter) + " (" + starterSymbol + ").");
            game.setUpdatedAtEpochMs(System.currentTimeMillis());
        } finally {
            game.getLock().unlock();
        }
    }

    public Map<String, Object> gameState(HttpSession session) {
        String sessionId = session.getId();
        touchSession(sessionId);
        GameSnapshot snapshot = gameSnapshotFor(sessionId);
        return snapshotToPayload(snapshot);
    }

    public Map<String, Object> gameState(HttpSession session, String requestedGameId) {
        String sessionId = session.getId();
        touchSession(sessionId);
        GameSnapshot snapshot = gameSnapshotFor(sessionId);
        if (!snapshot.hasGame || snapshot.gameId == null || !snapshot.gameId.equals(requestedGameId)) {
            return snapshotToPayload(GameSnapshot.empty());
        }
        return snapshotToPayload(snapshot);
    }

    public Map<String, Object> lobbyState(HttpSession session) {
        String sessionId = session.getId();
        long now = System.currentTimeMillis();
        touchSession(sessionId);
        cleanupStale(now);

        List<Map<String, Object>> users = new ArrayList<>();
        for (UserView view : listActiveUsers(sessionId, now)) {
            Map<String, Object> row = new HashMap<>();
            row.put("sessionId", view.sessionId());
            row.put("username", view.username());
            row.put("inGame", view.inGame());
            users.add(row);
        }

        List<Map<String, Object>> incoming = incomingInvites(session);

        List<Map<String, Object>> outgoing = new ArrayList<>();
        for (InviteView view : listOutgoingInvites(sessionId)) {
            Map<String, Object> row = new HashMap<>();
            row.put("inviteId", view.inviteId());
            row.put("to", view.from());
            row.put("status", view.status());
            outgoing.add(row);
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("activeUsers", users);
        payload.put("incomingInvites", incoming);
        payload.put("outgoingInvites", outgoing);
        payload.put("currentGameId", normalizeCurrentGame(sessionId));
        return payload;
    }

    private Map<String, Object> snapshotToPayload(GameSnapshot snapshot) {

        Map<String, Object> payload = new HashMap<>();
        payload.put("hasGame", snapshot.hasGame);
        payload.put("gameId", snapshot.gameId);
        payload.put("board", snapshot.board);
        payload.put("status", snapshot.status);
        payload.put("message", snapshot.message);
        payload.put("turnText", snapshot.turnText);
        payload.put("canPlay", snapshot.canPlay);
        payload.put("mySymbol", snapshot.mySymbol);
        payload.put("opponent", snapshot.opponent);
        payload.put("starter", snapshot.starter);
        payload.put("starterSymbol", snapshot.starterSymbol);
        return payload;
    }

    public List<Map<String, Object>> incomingInvites(HttpSession session) {
        touchSession(session.getId());
        List<Map<String, Object>> out = new ArrayList<>();
        for (InviteView view : listIncomingInvites(session.getId())) {
            Map<String, Object> row = new HashMap<>();
            row.put("inviteId", view.inviteId);
            row.put("from", view.from);
            row.put("status", view.status);
            out.add(row);
        }
        return out;
    }

    private void registerSessionUser(String sessionId, String username, long now) {
        usersBySession.compute(sessionId, (sid, oldUser) -> {
            if (oldUser == null) {
                return new SessionUser(sessionId, username, now);
            }
            oldUser.touch(now);
            return oldUser;
        });
        scores.computeIfAbsent(sessionId, k -> new SessionScore());
    }

    private void touchSession(String sessionId) {
        SessionUser user = usersBySession.get(sessionId);
        if (user != null) {
            user.touch(System.currentTimeMillis());
        }
    }

    private String normalizeCurrentGame(String sessionId) {
        String gameId = sessionToGame.get(sessionId);
        if (gameId == null) {
            return null;
        }
        if (!games.containsKey(gameId)) {
            sessionToGame.remove(sessionId);
            return null;
        }
        return gameId;
    }

    private void cleanupStale(long now) {
        usersBySession.values().removeIf(u -> now - u.getLastSeenEpochMs() > ACTIVE_USER_WINDOW_MS);

        for (String sessionId : new ArrayList<>(sessionToGame.keySet())) {
            String gameId = sessionToGame.get(sessionId);
            if (gameId != null && !games.containsKey(gameId)) {
                sessionToGame.remove(sessionId);
            }
        }

        invites.values().removeIf(i -> now - i.getCreatedAtEpochMs() > INVITE_TTL_MS && i.getStatus() == InviteStatus.PENDING);
    }

    private String createGame(GameInvite invite) {
        String gameId = UUID.randomUUID().toString();
        MultiplayerGame game = new MultiplayerGame(
                gameId,
                invite.getInviterSessionId(),
                invite.getInviterUsername(),
                invite.getInvitedSessionId(),
                invite.getInvitedUsername(),
                System.currentTimeMillis()
        );
        games.put(gameId, game);
        sessionToGame.put(invite.getInviterSessionId(), gameId);
        sessionToGame.put(invite.getInvitedSessionId(), gameId);
        return gameId;
    }

    private List<UserView> listActiveUsers(String currentSessionId, long now) {
        List<UserView> users = new ArrayList<>();
        for (SessionUser user : usersBySession.values()) {
            if (user.getSessionId().equals(currentSessionId)) {
                continue;
            }
            if (now - user.getLastSeenEpochMs() > ACTIVE_USER_WINDOW_MS) {
                continue;
            }
            users.add(new UserView(
                    user.getSessionId(),
                    user.getUsername(),
                    sessionToGame.containsKey(user.getSessionId())
            ));
        }

        users.sort(Comparator.comparing(UserView::username));
        return users;
    }

    private List<InviteView> listIncomingInvites(String sessionId) {
        List<InviteView> rows = new ArrayList<>();
        for (GameInvite invite : invites.values()) {
            if (!invite.getInvitedSessionId().equals(sessionId)) {
                continue;
            }
            rows.add(new InviteView(invite.getInviteId(), invite.getInviterUsername(), invite.getStatus().name()));
        }
        rows.sort(Comparator.comparing(InviteView::from));
        return rows;
    }

    private List<InviteView> listOutgoingInvites(String sessionId) {
        List<InviteView> rows = new ArrayList<>();
        for (GameInvite invite : invites.values()) {
            if (!invite.getInviterSessionId().equals(sessionId)) {
                continue;
            }
            rows.add(new InviteView(invite.getInviteId(), invite.getInvitedUsername(), invite.getStatus().name()));
        }
        rows.sort(Comparator.comparing(InviteView::from));
        return rows;
    }

    private GameSnapshot gameSnapshotFor(String sessionId) {
        String gameId = normalizeCurrentGame(sessionId);
        if (gameId == null) {
            return GameSnapshot.empty();
        }

        MultiplayerGame game = games.get(gameId);
        if (game == null || !game.isParticipant(sessionId)) {
            sessionToGame.remove(sessionId);
            return GameSnapshot.empty();
        }

        char[] copy = game.getBoard().clone();
        List<String> board = new ArrayList<>();
        for (char c : copy) {
            board.add(c == '-' ? "" : String.valueOf(c));
        }

        boolean canPlay = game.getStatus() == GameStatus.IN_PROGRESS && sessionId.equals(game.getTurnSessionId());
        String turnText = game.getStatus() == GameStatus.IN_PROGRESS
                ? "Turno: " + usernameOf(game, game.getTurnSessionId())
                : "Partita conclusa";

        return new GameSnapshot(
                true,
                gameId,
                board,
                game.getStatus().name(),
                game.getMessage(),
                turnText,
                canPlay,
                String.valueOf(game.getSymbolFor(sessionId)),
                game.getOpponentUsername(sessionId),
                usernameOf(game, game.getRoundStarterSessionId()),
                String.valueOf(game.getSymbolFor(game.getRoundStarterSessionId()))
        );
    }

    private String usernameOf(MultiplayerGame game, String sessionId) {
        if (game.getPlayerXSessionId().equals(sessionId)) {
            return game.getPlayerXUsername();
        }
        if (game.getPlayerOSessionId().equals(sessionId)) {
            return game.getPlayerOUsername();
        }
        return "?";
    }

    private void updateScoresOnWin(MultiplayerGame game, String winnerSessionId) {
        String loserSessionId = winnerSessionId.equals(game.getPlayerXSessionId())
                ? game.getPlayerOSessionId()
                : game.getPlayerXSessionId();

        scores.computeIfAbsent(winnerSessionId, k -> new SessionScore()).wins.incrementAndGet();
        scores.computeIfAbsent(loserSessionId, k -> new SessionScore()).losses.incrementAndGet();
    }

    private void updateScoresOnDraw(MultiplayerGame game) {
        scores.computeIfAbsent(game.getPlayerXSessionId(), k -> new SessionScore()).draws.incrementAndGet();
        scores.computeIfAbsent(game.getPlayerOSessionId(), k -> new SessionScore()).draws.incrementAndGet();
    }

    private boolean isBoardFull(char[] board) {
        for (char c : board) {
            if (c == '-') {
                return false;
            }
        }
        return true;
    }

    private boolean isWinner(char[] b, char p) {
        int[][] lines = {
                {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
                {0, 3, 6}, {1, 4, 7}, {2, 5, 8},
                {0, 4, 8}, {2, 4, 6}
        };

        for (int[] line : lines) {
            if (b[line[0]] == p && b[line[1]] == p && b[line[2]] == p) {
                return true;
            }
        }
        return false;
    }

    private void writeCookie(HttpServletResponse response, String name, String value) {
        Cookie cookie = new Cookie(name, encode(value));
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(COOKIE_AGE_SECONDS);
        response.addCookie(cookie);
    }

    public String readCookie(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return decode(cookie.getValue());
            }
        }
        return null;
    }

    private String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    public record UserView(String sessionId, String username, boolean inGame) {}

    public record InviteView(String inviteId, String from, String status) {}

    private record GameSnapshot(
            boolean hasGame,
            String gameId,
            List<String> board,
            String status,
            String message,
            String turnText,
            boolean canPlay,
            String mySymbol,
            String opponent,
            String starter,
            String starterSymbol
    ) {
        static GameSnapshot empty() {
            return new GameSnapshot(
                    false,
                    null,
                    List.of("", "", "", "", "", "", "", "", ""),
                    "WAITING",
                    "Nessuna partita attiva. Invia o accetta un invito.",
                    "In attesa partita",
                    false,
                    "-",
                    "",
                    "",
                    ""
            );
        }
    }

    private static class SessionScore {
        private final AtomicInteger wins = new AtomicInteger(0);
        private final AtomicInteger losses = new AtomicInteger(0);
        private final AtomicInteger draws = new AtomicInteger(0);
    }
}
