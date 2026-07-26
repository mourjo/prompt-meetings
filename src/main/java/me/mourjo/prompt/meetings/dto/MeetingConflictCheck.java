package me.mourjo.prompt.meetings.dto;

import java.time.LocalDateTime;

public record MeetingConflictCheck(
    Long id,
    String title,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String timezone,
    String calendarName,
    double calendarPriority
) {}
