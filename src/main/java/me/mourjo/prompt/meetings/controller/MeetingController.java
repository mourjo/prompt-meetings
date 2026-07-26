package me.mourjo.prompt.meetings.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import me.mourjo.prompt.meetings.dto.*;
import me.mourjo.prompt.meetings.exception.BadRequestException;
import me.mourjo.prompt.meetings.exception.ForbiddenException;
import me.mourjo.prompt.meetings.exception.NotFoundException;
import me.mourjo.prompt.meetings.repository.InvitationRepository;
import me.mourjo.prompt.meetings.repository.MeetingRepository;
import me.mourjo.prompt.meetings.repository.UserRepository;
import me.mourjo.prompt.meetings.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneId;
import java.util.List;

@RestController
@Tag(name = "Meetings & Invitations", description = "Endpoints for creating/viewing meetings and managing invitations")
public class MeetingController {

    private final MeetingRepository meetingRepository;
    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final AuthService authService;

    public MeetingController(
            MeetingRepository meetingRepository,
            InvitationRepository invitationRepository,
            UserRepository userRepository,
            AuthService authService) {
        this.meetingRepository = meetingRepository;
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.authService = authService;
    }

    @PostMapping("/meetings")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a meeting with a start and end time")
    public MeetingResponse createMeeting(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername,
            @Valid @RequestBody CreateMeetingRequest request) {

        authService.authenticate(xUsername);

        if (request.startTime().isAfter(request.endTime()) || request.startTime().isEqual(request.endTime())) {
            throw new BadRequestException("Start time must be before end time");
        }

        try {
            ZoneId.of(request.timezone());
        } catch (Exception e) {
            throw new BadRequestException("Invalid timezone: " + request.timezone());
        }

        Long meetingId = meetingRepository.save(
            request.title(),
            request.startTime(),
            request.endTime(),
            request.timezone(),
            xUsername
        );

        return new MeetingResponse(
            meetingId,
            request.title(),
            request.startTime(),
            request.endTime(),
            request.timezone(),
            xUsername,
            "ORGANIZER"
        );
    }

    @GetMapping("/meetings")
    @Operation(summary = "View all meetings of the current user")
    public List<MeetingResponse> getMyMeetings(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername) {

        authService.authenticate(xUsername);
        return meetingRepository.findMeetingsForUser(xUsername);
    }

    @PostMapping("/meetings/{meetingId}/invites")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Invite another user to a meeting")
    public void inviteUser(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername,
            @PathVariable Long meetingId,
            @Valid @RequestBody InviteUserRequest request) {

        authService.authenticate(xUsername);

        if (!meetingRepository.existsById(meetingId)) {
            throw new NotFoundException("Meeting not found with ID: " + meetingId);
        }

        String organizer = meetingRepository.getOrganizerUsername(meetingId)
                .orElseThrow(() -> new NotFoundException("Meeting organizer not found"));

        if (!organizer.equalsIgnoreCase(xUsername)) {
            throw new ForbiddenException("Only the meeting organizer can invite members");
        }

        String invitee = request.inviteeUsername();
        if (!userRepository.existsByUsername(invitee)) {
            throw new BadRequestException("Invitee user '" + invitee + "' does not exist");
        }

        if (organizer.equalsIgnoreCase(invitee)) {
            throw new BadRequestException("Organizer cannot invite themselves");
        }

        invitationRepository.saveInvitation(meetingId, invitee);
    }

    @PostMapping("/meetings/{meetingId}/invites/accept")
    @Operation(summary = "Accept an invite to a meeting")
    public void acceptInvite(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername,
            @PathVariable Long meetingId) {

        authService.authenticate(xUsername);

        if (!meetingRepository.existsById(meetingId)) {
            throw new NotFoundException("Meeting not found with ID: " + meetingId);
        }

        invitationRepository.getInvitationStatus(meetingId, xUsername)
                .orElseThrow(() -> new ForbiddenException("Only an invited member can respond to an existing invitation"));

        invitationRepository.updateStatus(meetingId, xUsername, "ACCEPTED");
    }

    @PostMapping("/meetings/{meetingId}/invites/reject")
    @Operation(summary = "Reject an invite to a meeting")
    public void rejectInvite(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername,
            @PathVariable Long meetingId) {

        authService.authenticate(xUsername);

        if (!meetingRepository.existsById(meetingId)) {
            throw new NotFoundException("Meeting not found with ID: " + meetingId);
        }

        invitationRepository.getInvitationStatus(meetingId, xUsername)
                .orElseThrow(() -> new ForbiddenException("Only an invited member can respond to an existing invitation"));

        invitationRepository.updateStatus(meetingId, xUsername, "REJECTED");
    }

    @GetMapping("/users/{username}/invitations/pending")
    @Operation(summary = "View pending invitations for a specific user")
    public List<PendingInvitationResponse> getPendingInvitationsForUser(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername,
            @PathVariable String username) {

        authService.authenticate(xUsername);

        if (!xUsername.equalsIgnoreCase(username)) {
            throw new ForbiddenException("You can only view your own pending invitations");
        }

        return invitationRepository.findPendingInvitationsForUser(username);
    }

    @GetMapping("/meetings/invitations/pending")
    @Operation(summary = "View all pending invitations received by the current user")
    public List<PendingInvitationResponse> getMyPendingInvitations(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername) {

        authService.authenticate(xUsername);
        return invitationRepository.findPendingInvitationsForUser(xUsername);
    }
}
