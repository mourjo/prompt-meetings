package me.mourjo.prompt.meetings.service;

import me.mourjo.prompt.meetings.dto.*;
import me.mourjo.prompt.meetings.exception.BadRequestException;
import me.mourjo.prompt.meetings.exception.ForbiddenException;
import me.mourjo.prompt.meetings.exception.NotFoundException;
import me.mourjo.prompt.meetings.model.Meeting;
import me.mourjo.prompt.meetings.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final InvitationRepository invitationRepository;
    private final UserRepository userRepository;
    private final CalendarRepository calendarRepository;

    public MeetingService(
            MeetingRepository meetingRepository,
            InvitationRepository invitationRepository,
            UserRepository userRepository,
            CalendarRepository calendarRepository) {
        this.meetingRepository = meetingRepository;
        this.invitationRepository = invitationRepository;
        this.userRepository = userRepository;
        this.calendarRepository = calendarRepository;
    }

    @Transactional
    public MeetingResponse createMeeting(String xUsername, CreateMeetingRequest request) {
        if (!calendarRepository.existsById(request.calendarName())) {
            throw new BadRequestException("Calendar '" + request.calendarName() + "' does not exist");
        }

        Double p = calendarRepository.getPriority(request.calendarName());
        double newPriority = p != null ? p : 1.0;

        ZoneId newZoneId = ZoneId.of(request.timezone());
        ZonedDateTime newStart = request.startTime().atZone(newZoneId);
        ZonedDateTime newEnd = request.endTime().atZone(newZoneId);

        List<MeetingConflictCheck> existingMeetings = meetingRepository.findAcceptedMeetingsForConflictCheck(xUsername);
        List<MeetingConflictCheck> conflictingMeetings = new ArrayList<>();

        for (MeetingConflictCheck m : existingMeetings) {
            ZoneId extZoneId = ZoneId.of(m.timezone());
            ZonedDateTime extStart = m.startTime().atZone(extZoneId);
            ZonedDateTime extEnd = m.endTime().atZone(extZoneId);

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

    public List<MeetingResponse> getMyMeetings(String xUsername) {
        return meetingRepository.findMeetingsForUser(xUsername).stream()
                .map(m -> new MeetingResponse(
                    m.id(),
                    m.title(),
                    m.startTime(),
                    m.endTime(),
                    m.timezone(),
                    m.organizerUsername(),
                    m.userStatus(),
                    m.calendarName()
                ))
                .collect(Collectors.toList());
    }

    public void inviteUser(String xUsername, Long meetingId, String inviteeUsername) {
        if (!meetingRepository.existsById(meetingId)) {
            throw new NotFoundException("Meeting not found with ID: " + meetingId);
        }

        String organizer = meetingRepository.getOrganizerUsername(meetingId)
                .orElseThrow(() -> new NotFoundException("Meeting organizer not found"));

        if (!organizer.equalsIgnoreCase(xUsername)) {
            throw new ForbiddenException("Only the meeting organizer can invite members");
        }

        if (!userRepository.existsByUsername(inviteeUsername)) {
            throw new BadRequestException("Invitee user '" + inviteeUsername + "' does not exist");
        }

        if (organizer.equalsIgnoreCase(inviteeUsername)) {
            throw new BadRequestException("Organizer cannot invite themselves");
        }

        invitationRepository.saveInvitation(meetingId, inviteeUsername);
    }

    public void acceptInvite(String xUsername, Long meetingId) {
        if (!meetingRepository.existsById(meetingId)) {
            throw new NotFoundException("Meeting not found with ID: " + meetingId);
        }

        invitationRepository.getInvitationStatus(meetingId, xUsername)
                .orElseThrow(() -> new ForbiddenException("Only an invited member can respond to an existing invitation"));

        invitationRepository.updateStatus(meetingId, xUsername, "ACCEPTED");
    }

    public void rejectInvite(String xUsername, Long meetingId) {
        if (!meetingRepository.existsById(meetingId)) {
            throw new NotFoundException("Meeting not found with ID: " + meetingId);
        }

        invitationRepository.getInvitationStatus(meetingId, xUsername)
                .orElseThrow(() -> new ForbiddenException("Only an invited member can respond to an existing invitation"));

        invitationRepository.updateStatus(meetingId, xUsername, "REJECTED");
    }

    public List<PendingInvitationResponse> getMyPendingInvitations(String xUsername) {
        return invitationRepository.findPendingInvitationsForUser(xUsername).stream()
                .map(info -> {
                    long durationMinutes = Duration.between(info.startTime(), info.endTime()).toMinutes();
                    String durationStr = durationMinutes + " minutes";
                    return new PendingInvitationResponse(
                        info.meetingId(),
                        info.meetingName(),
                        info.invitedBy(),
                        info.startTime(),
                        info.endTime(),
                        info.timezone(),
                        durationMinutes,
                        durationStr
                    );
                })
                .collect(Collectors.toList());
    }
}
