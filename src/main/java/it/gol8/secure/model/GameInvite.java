package it.gol8.secure.model;

public class GameInvite {

    private final String inviteId;
    private final String inviterSessionId;
    private final String inviterUsername;
    private final String invitedSessionId;
    private final String invitedUsername;
    private volatile InviteStatus status;
    private final long createdAtEpochMs;

    public GameInvite(
            String inviteId,
            String inviterSessionId,
            String inviterUsername,
            String invitedSessionId,
            String invitedUsername,
            InviteStatus status,
            long createdAtEpochMs
    ) {
        this.inviteId = inviteId;
        this.inviterSessionId = inviterSessionId;
        this.inviterUsername = inviterUsername;
        this.invitedSessionId = invitedSessionId;
        this.invitedUsername = invitedUsername;
        this.status = status;
        this.createdAtEpochMs = createdAtEpochMs;
    }

    public String getInviteId() {
        return inviteId;
    }

    public String getInviterSessionId() {
        return inviterSessionId;
    }

    public String getInviterUsername() {
        return inviterUsername;
    }

    public String getInvitedSessionId() {
        return invitedSessionId;
    }

    public String getInvitedUsername() {
        return invitedUsername;
    }

    public InviteStatus getStatus() {
        return status;
    }

    public void setStatus(InviteStatus status) {
        this.status = status;
    }

    public long getCreatedAtEpochMs() {
        return createdAtEpochMs;
    }
}
