package it.gol8.secure.controller;

import it.gol8.secure.service.MultiplayerTicTacToeService;
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

    public WebController(MultiplayerTicTacToeService multiplayerService) {
        this.multiplayerService = multiplayerService;
    }

    @GetMapping("/")
    public String index(Model m) {
        m.addAttribute("nome", "franco");
        return "index"; // index.html
    }

    @GetMapping("/access_denied")
    public String mAccessDenied() {
        return "403";
    }

    @GetMapping("/login")
    public String mLogin() {
        return "login";
    }
    
    @GetMapping("/dashboard")
    public String mDashboard(Model model, Principal principal, HttpSession session, HttpServletResponse response) {
        multiplayerService.populateDashboard(model, principal.getName(), session, response);
        return "dashboard";
    }

    @GetMapping("/click/{index}")
    public String clickCell(@PathVariable int index, HttpSession session) {
        multiplayerService.playMove(session, index);
        return "redirect:/dashboard";
    }

    @GetMapping("/lobby/invite/{sessionId}")
    public String sendInvite(@PathVariable("sessionId") String targetSessionId, Principal principal, HttpSession session) {
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

    @GetMapping("/game/leave")
    public String leaveGame(HttpSession session) {
        multiplayerService.leaveGame(session);
        return "redirect:/dashboard";
    }

    @GetMapping("/api/game/state")
    @ResponseBody
    public Map<String, Object> gameState(HttpSession session) {
        return multiplayerService.gameState(session);
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
    
}
