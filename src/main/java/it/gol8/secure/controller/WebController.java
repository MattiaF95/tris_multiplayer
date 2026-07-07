package it.gol8.secure.controller;

import it.gol8.secure.service.MultiplayerTicTacToeService;
import it.gol8.secure.service.TicTacToeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.security.Principal;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class WebController {

    private final MultiplayerTicTacToeService multiplayerService;
    private final TicTacToeService singleplayerService;

    public WebController(MultiplayerTicTacToeService multiplayerService, TicTacToeService singleplayerService) {
        this.multiplayerService = multiplayerService;
        this.singleplayerService = singleplayerService;
    }

    @GetMapping("/")
    public String index(Model m) {
        m.addAttribute("nome", "franco");
        return "index"; // index.html
    }

    @GetMapping("/access-denied")
    public String mAccessDenied() {
        return "403";
    }

    @GetMapping("/login")
    public String mLogin() {
        return "login";
    }

    @GetMapping("/dashboard")
    public String mDashboard(Model model, Principal principal, HttpSession session, HttpServletResponse response,
            HttpServletRequest request) {
        String gameMode = readCookie(request, "game_mode");

        if ("multiplayer".equals(gameMode)) {
            // Multiplayer mode: use MultiplayerTicTacToeService
            multiplayerService.populateDashboard(model, principal.getName(), session, response);
        } else {
            // Single-player mode: use TicTacToeService
            singleplayerService.populateDashboard(model, request, response);
        }

        return "dashboard";
    }

    @GetMapping("/click/{index}")
    public String clickCell(@PathVariable int index, HttpServletRequest request, HttpServletResponse response) {
        String gameMode = readCookie(request, "game_mode");

        if ("multiplayer".equals(gameMode)) {
            HttpSession session = request.getSession();
            multiplayerService.playMove(session, index);
        } else {
            singleplayerService.handleClick(index, request, response);
        }

        return "redirect:/dashboard";
    }

    @GetMapping("/lobby/invite/{sessionId}")
    public String sendInvite(@PathVariable("sessionId") String targetSessionId, Principal principal,
            HttpSession session) {
        multiplayerService.sendInvite(session, principal.getName(), targetSessionId);
        return "redirect:/dashboard";
    }

    @GetMapping("/lobby/invite/{inviteId}/accept")
    public String acceptInvite(@PathVariable String inviteId, HttpSession session, HttpServletResponse response) {
        multiplayerService.respondInvite(session, inviteId, true, response);
        return "redirect:/dashboard";
    }

    @GetMapping("/lobby/invite/{inviteId}/reject")
    public String rejectInvite(@PathVariable String inviteId, HttpSession session, HttpServletResponse response) {
        multiplayerService.respondInvite(session, inviteId, false, response);
        return "redirect:/dashboard";
    }

    @GetMapping("/game/reset")
    public String resetGame(HttpSession session) {
        multiplayerService.restartCurrentGame(session);
        return "redirect:/dashboard";
    }

    @GetMapping("/game/singleplayer")
    public String startSingleplayer(HttpServletRequest request, HttpServletResponse response) {
        writeCookie(response, "game_mode", "singleplayer");
        singleplayerService.resetBoard(request, response);
        return "redirect:/dashboard";
    }

    @GetMapping("/game/multiplayer")
    public String startMultiplayer(HttpSession session, HttpServletResponse response) {
        writeCookie(response, "game_mode", "multiplayer");
        multiplayerService.leaveGame(session);
        return "redirect:/dashboard";
    }

    @GetMapping("/game/leave")
    public String leaveGame(HttpSession session, HttpServletResponse response) {
        writeCookie(response, "game_mode", "");
        multiplayerService.leaveGame(session);
        return "redirect:/dashboard";
    }

    @GetMapping("/api/game/state")
    @ResponseBody
    public Map<String, Object> gameState(HttpServletRequest request, HttpSession session) {
        String gameMode = readCookie(request, "game_mode");

        if ("multiplayer".equals(gameMode)) {
            return multiplayerService.gameState(session);
        }

        return singleplayerGameState(request);
    }

    @GetMapping("/api/game/{gameId}/state")
    @ResponseBody
    public Map<String, Object> gameStateById(@PathVariable String gameId, HttpSession session) {
        return multiplayerService.gameState(session, gameId);
    }

    @GetMapping("/api/lobby/invites")
    @ResponseBody
    public List<Map<String, Object>> incomingInvites(HttpSession session) {
        return multiplayerService.incomingInvites(session);
    }

    @GetMapping("/api/lobby/state")
    @ResponseBody
    public Map<String, Object> lobbyState(HttpSession session) {
        return multiplayerService.lobbyState(session);
    }

    @GetMapping("/profile")
    public String profile() {
        return "profile";
    }

    @GetMapping("/public-info")
    public String mPublicInfo() {
        return "public-info";
    }

    @GetMapping("/admin-only")
    public String mAdminOnly() {
        return "admin-only";
    }

    private String readCookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private void writeCookie(HttpServletResponse response, String name, String value) {
        jakarta.servlet.http.Cookie cookie = new jakarta.servlet.http.Cookie(name, value);
        cookie.setPath("/");
        cookie.setMaxAge(value == null || value.isEmpty() ? 0 : 60 * 60 * 24);
        response.addCookie(cookie);
    }

    private Map<String, Object> singleplayerGameState(HttpServletRequest request) {
        return singleplayerService.gameStateAsMap(request);
    }
}
