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
import me.mourjo.prompt.meetings.repository.CalendarRepository;
import me.mourjo.prompt.meetings.repository.InvitationRepository;
import me.mourjo.prompt.meetings.repository.MeetingRepository;
import me.mourjo.prompt.meetings.repository.UserRepository;
import me.mourjo.prompt.meetings.model.Meeting;
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
    private final CalendarRepository calendarRepository;
    private final AuthService authService;

    public MeetingController(
            MeetingRepository meetingRepository,
            InvitationRepository invitationRepository,
            UserRepository userRepository,
            CalendarRepository calendarRepository,
            AuthService authService) {
        this.meetingRepository = meetingRepository;
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.calendarRepository = calendarRepository;
        this.authService = authService;
    }

    @PostMapping("/meetings")
    @ResponseStatus(HttpStatus.CREATED)
    @org.springframework.transaction.annotation.Transactional
    @Operation(summary = "Create a meeting with a start and end time")
    public MeetingResponse createMeeting(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername,
            @Valid @RequestBody CreateMeetingRequest request) {

        authService.authenticate(xUsername);

        if (request.startTime().isAfter(request.endTime()) || request.startTime().isEqual(request.endTime())) {
            throw new BadRequestException("Start time must be before end time");
        }

        ZoneId newZoneId;
        try {
            newZoneId = ZoneId.of(request.timezone());
        } catch (Exception e) {
            throw new BadRequestException("Invalid timezone: " + request.timezone());
        }

        if (!calendarRepository.existsByName(request.calendarName())) {
            throw new BadRequestException("Calendar '" + request.calendarName() + "' does not exist");
        }

        double newPriority = calendarRepository.getPriority(request.calendarName());
        java.time.ZonedDateTime newStart = request.startTime().atZone(newZoneId);
        java.time.ZonedDateTime newEnd = request.endTime().atZone(newZoneId);

        List<MeetingConflictCheck> existingMeetings = meetingRepository.findAcceptedMeetingsForConflictCheck(xUsername);
        List<MeetingConflictCheck> conflictingMeetings = new java.util.ArrayList<>();

        for (MeetingConflictCheck m : existingMeetings) {
            ZoneId extZoneId = ZoneId.of(m.timezone());
            java.time.ZonedDateTime extStart = m.startTime().atZone(extZoneId);
            java.time.ZonedDateTime extEnd = m.endTime().atZone(extZoneId);

            if (extStart.isBefore(newEnd) && newStart.isBefore(extEnd)) {
                conflictingMeetings.add(m);
            }
        }

        for (MeetingConflictCheck m : conflictingMeetings) {
            if (m.calendarPriority() >= newPriority) {
                throw new BadRequestException("Conflict with meeting '" + m.title() + "' in calendar '" + m.calendarName() + "' (priority: " + m.calendarPriority() + " >= " + newPriority + ")");
            }
        }

        // Reject lower priority conflicting meetings
        for (MeetingConflictCheck m : conflictingMeetings) {
            invitationRepository.updateStatus(m.id(), xUsername, "REJECTED");
        }

        Meeting meeting = new Meeting(
            request.title(),
            request.startTime(),
            request.endTime(),
            request.timezone(),
            xUsername,
            request.calendarName()
        );
        Meeting saved = meetingRepository.save(meeting);
        Long meetingId = saved.getId();

        // Auto-create and accept invitation for the organizer
        invitationRepository.saveInvitation(meetingId, xUsername);
        invitationRepository.updateStatus(meetingId, xUsername, "ACCEPTED");

        return new MeetingResponse(
            meetingId,
            request.title(),
            request.startTime(),
            request.endTime(),
            request.timezone(),
            xUsername,
            "ACCEPTED",
            request.calendarName()
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

    @GetMapping("/meetings/invitations/pending")
    @Operation(summary = "View all pending invitations received by the current user")
    public List<PendingInvitationResponse> getMyPendingInvitations(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername) {

        authService.authenticate(xUsername);
        return invitationRepository.findPendingInvitationsForUser(xUsername);
    }

    @PostMapping("/calendars")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new calendar")
    public CalendarResponse createCalendar(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername,
            @Valid @RequestBody CreateCalendarRequest request) {

        authService.authenticate(xUsername);

        if ("default".equalsIgnoreCase(request.name())) {
            throw new BadRequestException("Cannot create or override the default calendar");
        }

        calendarRepository.save(request.name(), request.priority());
        return new CalendarResponse(request.name(), request.priority());
    }

    @GetMapping("/calendars")
    @Operation(summary = "Fetch all calendars")
    public List<CalendarResponse> getAllCalendars(
            @Parameter(in = ParameterIn.HEADER, name = "X-USERNAME", required = true, schema = @Schema(type = "string"))
            @RequestHeader("X-USERNAME") String xUsername) {

        authService.authenticate(xUsername);
        return calendarRepository.findAll();
    }
}
