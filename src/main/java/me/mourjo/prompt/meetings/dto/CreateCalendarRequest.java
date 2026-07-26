package me.mourjo.prompt.meetings.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCalendarRequest(
    @NotBlank(message = "Calendar name is required")
    String name,

    @NotNull(message = "Priority is required")
    Double priority
) {}
