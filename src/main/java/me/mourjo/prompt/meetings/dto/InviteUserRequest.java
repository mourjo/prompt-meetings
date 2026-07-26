package me.mourjo.prompt.meetings.dto;

import jakarta.validation.constraints.NotBlank;

public record InviteUserRequest(
    @NotBlank(message = "Invitee username is required")
    String inviteeUsername
) {}
