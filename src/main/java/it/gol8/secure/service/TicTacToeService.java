package it.gol8.secure.service;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class TicTacToeService {

    private static final String COOKIE_BOARD = "ttt_board";
    private static final String COOKIE_STATS = "ttt_stats";
    private static final String COOKIE_STATUS = "ttt_status";
    private static final String COOKIE_MESSAGE = "ttt_message";
    private static final int COOKIE_AGE_SECONDS = 60 * 60 * 24 * 30;

    private static final String STATUS_PLAYING = "PLAYING";
    private static final String STATUS_USER_WIN = "USER_WIN";
    private static final String STATUS_CPU_WIN = "CPU_WIN";
    private static final String STATUS_DRAW = "DRAW";

    public void populateDashboard(Model model, HttpServletRequest request, HttpServletResponse response) {
        GameState state = loadState(request);
        saveState(response, state);

        List<String> boardForView = new ArrayList<>();
        for (char value : state.board) {
            boardForView.add(value == '-' ? "" : String.valueOf(value));
        }

        model.addAttribute("board", boardForView);
        model.addAttribute("wins", state.wins);
        model.addAttribute("losses", state.losses);
        model.addAttribute("draws", state.draws);
        model.addAttribute("played", state.wins + state.losses + state.draws);
        model.addAttribute("gameStatus", state.status);
        model.addAttribute("resultMessage", state.message);
    }

    public void handleClick(int index, HttpServletRequest request, HttpServletResponse response) {
        GameState state = loadState(request);

        if (index < 0 || index > 8) {
            state.message = "Mossa non valida.";
            saveState(response, state);
            return;
        }

        if (!STATUS_PLAYING.equals(state.status)) {
            state.board = emptyBoard();
            state.status = STATUS_PLAYING;
            state.message = "Nuova partita avviata.";
        }

        if (state.board[index] != '-') {
            state.message = "Casella gia occupata, scegli una casella libera.";
            saveState(response, state);
            return;
        }

        state.board[index] = 'X';

        if (isWinner(state.board, 'X')) {
            state.wins++;
            state.status = STATUS_USER_WIN;
            state.message = "Hai vinto questa partita!";
            saveState(response, state);
            return;
        }

        if (isBoardFull(state.board)) {
            state.draws++;
            state.status = STATUS_DRAW;
            state.message = "Pareggio! Nessuna casella libera.";
            saveState(response, state);
            return;
        }

        cpuRandomMove(state.board);

        if (isWinner(state.board, 'O')) {
            state.losses++;
            state.status = STATUS_CPU_WIN;
            state.message = "Hai perso: il cerchio O ha vinto.";
            saveState(response, state);
            return;
        }

        if (isBoardFull(state.board)) {
            state.draws++;
            state.status = STATUS_DRAW;
            state.message = "Pareggio!";
            saveState(response, state);
            return;
        }

        state.status = STATUS_PLAYING;
        state.message = "Mossa registrata. Tocca di nuovo a X.";
        saveState(response, state);
    }

    public void resetBoard(HttpServletRequest request, HttpServletResponse response) {
        GameState state = loadState(request);
        state.board = emptyBoard();
        state.status = STATUS_PLAYING;
        state.message = "Board resettata. Nuova partita pronta.";
        saveState(response, state);
    }

    private void cpuRandomMove(char[] board) {
        List<Integer> free = new ArrayList<>();
        for (int i = 0; i < board.length; i++) {
            if (board[i] == '-') {
                free.add(i);
            }
        }

        if (free.isEmpty()) {
            return;
        }

        int choice = free.get(ThreadLocalRandom.current().nextInt(free.size()));
        board[choice] = 'O';
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

    private GameState loadState(HttpServletRequest request) {
        String boardCookie = readCookie(request, COOKIE_BOARD);
        String statsCookie = readCookie(request, COOKIE_STATS);
        String statusCookie = readCookie(request, COOKIE_STATUS);
        String messageCookie = readCookie(request, COOKIE_MESSAGE);

        char[] board = parseBoard(boardCookie);
        int[] stats = parseStats(statsCookie);

        GameState state = new GameState();
        state.board = board;
        state.wins = stats[0];
        state.losses = stats[1];
        state.draws = stats[2];
        state.status = (statusCookie == null || statusCookie.isBlank()) ? STATUS_PLAYING : statusCookie;
        state.message = (messageCookie == null || messageCookie.isBlank())
                ? "Inizia la partita: piazza una X su una casella libera."
            : messageCookie;

        return state;
    }

    private void saveState(HttpServletResponse response, GameState state) {
        writeCookie(response, COOKIE_BOARD, new String(state.board));
        writeCookie(response, COOKIE_STATS, state.wins + "," + state.losses + "," + state.draws);
        writeCookie(response, COOKIE_STATUS, state.status);
        writeCookie(response, COOKIE_MESSAGE, state.message);
    }

    private String readCookie(HttpServletRequest request, String name) {
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

    private void writeCookie(HttpServletResponse response, String name, String value) {
        Cookie cookie = new Cookie(name, encode(value));
        cookie.setPath("/");
        cookie.setMaxAge(COOKIE_AGE_SECONDS);
        cookie.setHttpOnly(true);
        response.addCookie(cookie);
    }

    private char[] parseBoard(String raw) {
        if (raw == null || raw.length() != 9) {
            return emptyBoard();
        }

        char[] board = raw.toCharArray();
        for (char c : board) {
            if (c != 'X' && c != 'O' && c != '-') {
                return emptyBoard();
            }
        }

        return board;
    }

    private int[] parseStats(String raw) {
        if (raw == null || raw.isBlank()) {
            return new int[] {0, 0, 0};
        }

        String[] parts = raw.split(",");
        if (parts.length != 3) {
            return new int[] {0, 0, 0};
        }

        try {
            return new int[] {
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2])
            };
        } catch (NumberFormatException ex) {
            return new int[] {0, 0, 0};
        }
    }

    private char[] emptyBoard() {
        char[] board = new char[9];
        Arrays.fill(board, '-');
        return board;
    }

    private String encode(String text) {
        return URLEncoder.encode(text, StandardCharsets.UTF_8);
    }

    private String decode(String text) {
        return URLDecoder.decode(text, StandardCharsets.UTF_8);
    }

    private static class GameState {
        private char[] board;
        private int wins;
        private int losses;
        private int draws;
        private String status;
        private String message;
    }
}