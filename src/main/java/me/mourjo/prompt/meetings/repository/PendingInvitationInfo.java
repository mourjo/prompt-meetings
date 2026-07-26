package me.mourjo.prompt.meetings.repository;

import java.time.LocalDateTime;

public record PendingInvitationInfo(
    Long meetingId,
    String meetingName,
    String invitedBy,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String timezone
) {}
