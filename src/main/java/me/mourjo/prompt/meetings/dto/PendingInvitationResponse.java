package me.mourjo.prompt.meetings.dto;

import java.time.LocalDateTime;

public record PendingInvitationResponse(
    Long meetingId,
    String meetingName,
    String invitedBy,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String timezone,
    long durationMinutes,
    String duration
) {}
