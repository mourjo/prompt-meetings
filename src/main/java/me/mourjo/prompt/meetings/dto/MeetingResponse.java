package me.mourjo.prompt.meetings.dto;

import java.time.LocalDateTime;

public record MeetingResponse(
    Long id,
    String title,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String timezone,
    String organizerUsername,
    String userStatus
) {}
