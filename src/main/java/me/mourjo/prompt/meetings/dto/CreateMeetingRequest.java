package me.mourjo.prompt.meetings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record CreateMeetingRequest(
    @NotBlank(message = "Title is required")
    String title,

    @NotNull(message = "Start time is required")
    LocalDateTime startTime,

    @NotNull(message = "End time is required")
    LocalDateTime endTime,

    @NotBlank(message = "Timezone is required")
    String timezone,

    @NotBlank(message = "Calendar name is required")
    String calendarName
) {}
