package me.mourjo.prompt.meetings.repository;

import java.time.LocalDateTime;

public record UserMeetingInfo(
    Long id,
    String title,
    LocalDateTime startTime,
    LocalDateTime endTime,
    String timezone,
    String organizerUsername,
    String userStatus,
    String calendarName
) {}
