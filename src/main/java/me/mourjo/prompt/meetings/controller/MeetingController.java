package me.mourjo.prompt.meetings.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import me.mourjo.prompt.meetings.dto.*;
import me.mourjo.prompt.meetings.service.CalendarService;
import me.mourjo.prompt.meetings.service.MeetingService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@Tag(name = "Meetings & Invitations", description = "Endpoints for creating/viewing meetings and managing invitations")
public class MeetingController {

    private final MeetingService meetingService;
    private final CalendarService calendarService;

    public MeetingController(MeetingService meetingService, CalendarService calendarService) {
        this.meetingService = meetingService;
        this.calendarService = calendarService;
    }

    @PostMapping("/meetings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a meeting with a start and end time")
    public MeetingResponse createMeeting(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername,
            @Valid @RequestBody CreateMeetingRequest request) {
        return meetingService.createMeeting(xUsername, request);
    }

    @GetMapping("/meetings")
    @Operation(summary = "View all meetings of the current user")
    public List<MeetingResponse> getMyMeetings(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername) {
        return meetingService.getMyMeetings(xUsername);
    }

    @PostMapping("/meetings/{meetingId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Invite another user to a meeting")
    public void inviteUser(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername,
            @PathVariable Long meetingId,
            @Valid @RequestBody InviteUserRequest request) {
        meetingService.inviteUser(xUsername, meetingId, request.inviteeUsername());
    }

    @PostMapping("/meetings/{meetingId}/invites/accept")
    @Operation(summary = "Accept an invite to a meeting")
    public void acceptInvite(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername,
            @PathVariable Long meetingId) {
        meetingService.acceptInvite(xUsername, meetingId);
    }

    @PostMapping("/meetings/{meetingId}/invites/reject")
    @Operation(summary = "Reject an invite to a meeting")
    public void rejectInvite(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername,
            @PathVariable Long meetingId) {
        meetingService.rejectInvite(xUsername, meetingId);
    }

    @GetMapping("/meetings/invitations/pending")
    @Operation(summary = "View all pending invitations received by the current user")
    public List<PendingInvitationResponse> getMyPendingInvitations(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername) {
        return meetingService.getMyPendingInvitations(xUsername);
    }

    @PostMapping("/calendars")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new calendar")
    public CalendarResponse createCalendar(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername,
            @Valid @RequestBody CreateCalendarRequest request) {
        return calendarService.createCalendar(xUsername, request.name(), request.priority());
    }

    @GetMapping("/calendars")
    @Operation(summary = "Fetch all calendars")
    public List<CalendarResponse> getAllCalendars(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername) {
        return calendarService.getAllCalendars(xUsername);
    }
}
