package it.gol8.secure.model;

public class SessionUser {

    private final String sessionId;
    private final String username;
    private volatile long lastSeenEpochMs;

    public SessionUser(String sessionId, String username, long lastSeenEpochMs) {
        this.sessionId = sessionId;
        this.username = username;
        this.lastSeenEpochMs = lastSeenEpochMs;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getUsername() {
        return username;
    }

    public long getLastSeenEpochMs() {
        return lastSeenEpochMs;
    }

    public void touch(long nowEpochMs) {
        this.lastSeenEpochMs = nowEpochMs;
    }
}
