package it.gol8.secure.model;

import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;

public class MultiplayerGame {

    private final String gameId;
    private final String playerXSessionId;
    private final String playerXUsername;
    private final String playerOSessionId;
    private final String playerOUsername;
    private final char[] board;
    private final ReentrantLock lock;

    private volatile String turnSessionId;
    private volatile String roundStarterSessionId;
    private volatile GameStatus status;
    private volatile String winnerSessionId;
    private volatile String message;
    private volatile long updatedAtEpochMs;

    public MultiplayerGame(
            String gameId,
            String playerXSessionId,
            String playerXUsername,
            String playerOSessionId,
            String playerOUsername,
            long nowEpochMs
    ) {
        this.gameId = gameId;
        this.playerXSessionId = playerXSessionId;
        this.playerXUsername = playerXUsername;
        this.playerOSessionId = playerOSessionId;
        this.playerOUsername = playerOUsername;
        this.board = new char[9];
        Arrays.fill(this.board, '-');
        this.turnSessionId = playerXSessionId;
        this.roundStarterSessionId = playerXSessionId;
        this.status = GameStatus.IN_PROGRESS;
        this.message = "Partita iniziata. Tocca a X.";
        this.updatedAtEpochMs = nowEpochMs;
        this.lock = new ReentrantLock();
    }

    public String getGameId() {
        return gameId;
    }

    public String getPlayerXSessionId() {
        return playerXSessionId;
    }

    public String getPlayerXUsername() {
        return playerXUsername;
    }

    public String getPlayerOSessionId() {
        return playerOSessionId;
    }

    public String getPlayerOUsername() {
        return playerOUsername;
    }

    public char[] getBoard() {
        return board;
    }

    public ReentrantLock getLock() {
        return lock;
    }

    public String getTurnSessionId() {
        return turnSessionId;
    }

    public void setTurnSessionId(String turnSessionId) {
        this.turnSessionId = turnSessionId;
    }

    public String getRoundStarterSessionId() {
        return roundStarterSessionId;
    }

    public void setRoundStarterSessionId(String roundStarterSessionId) {
        this.roundStarterSessionId = roundStarterSessionId;
    }

    public GameStatus getStatus() {
        return status;
    }

    public void setStatus(GameStatus status) {
        this.status = status;
    }

    public String getWinnerSessionId() {
        return winnerSessionId;
    }

    public void setWinnerSessionId(String winnerSessionId) {
        this.winnerSessionId = winnerSessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public long getUpdatedAtEpochMs() {
        return updatedAtEpochMs;
    }

    public void setUpdatedAtEpochMs(long updatedAtEpochMs) {
        this.updatedAtEpochMs = updatedAtEpochMs;
    }

    public boolean isParticipant(String sessionId) {
        return playerXSessionId.equals(sessionId) || playerOSessionId.equals(sessionId);
    }

    public char getSymbolFor(String sessionId) {
        if (playerXSessionId.equals(sessionId)) {
            return 'X';
        }
        if (playerOSessionId.equals(sessionId)) {
            return 'O';
        }
        return '-';
    }

    public String getOpponentUsername(String sessionId) {
        if (playerXSessionId.equals(sessionId)) {
            return playerOUsername;
        }
        if (playerOSessionId.equals(sessionId)) {
            return playerXUsername;
        }
        return "";
    }
}
