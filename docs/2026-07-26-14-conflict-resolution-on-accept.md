# Plan: Conflict Resolution on Accepting an Invitation

When a user accepts a meeting invitation, check for conflicts with their existing accepted meetings. Resolve conflicts based on calendar priorities.

## Proposed Changes

### 1. Update Core Service Logic
In [MeetingService.java](file:///Users/mourjo/repos/prompt-meetings/src/main/java/me/mourjo/prompt/meetings/service/MeetingService.java), annotate `acceptInvite` with `@Transactional` and add conflict resolution:
- Fetch the meeting details for the meeting being accepted.
- Get the calendar priority for the meeting being accepted.
- Retrieve all currently accepted meetings for the user.
- Find any conflicting meetings that overlap in time (adjusted for timezones).
- If any conflicting meeting has a calendar priority greater than or equal to the accepted meeting's priority, abort and throw `BadRequestException`.
- Otherwise (if all conflicting meetings have lower priority), reject those conflicting meetings by updating their invitation status to `REJECTED`.
- Finally, update the accepted meeting status to `ACCEPTED`.

```java
    @Transactional
    public void acceptInvite(String xUsername, Long meetingId) {
        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> new NotFoundException("Meeting not found with ID: " + meetingId));

        String status = invitationRepository.getInvitationStatus(meetingId, xUsername)
                .orElseThrow(() -> new ForbiddenException("Only an invited member can respond to an existing invitation"));

        if (!"PENDING".equalsIgnoreCase(status)) {
            throw new BadRequestException("Invitation is not in a pending state");
        }

        Double p = calendarRepository.getPriority(meeting.getCalendarName());
        double newPriority = p != null ? p : 1.0;

        ZoneId newZoneId = ZoneId.of(meeting.getTimezone());
        ZonedDateTime newStart = meeting.getStartTime().atZone(newZoneId);
        ZonedDateTime newEnd = meeting.getEndTime().atZone(newZoneId);

        List<MeetingConflictCheck> existingMeetings = meetingRepository.findAcceptedMeetingsForConflictCheck(xUsername);
        List<MeetingConflictCheck> conflictingMeetings = new ArrayList<>();

        for (MeetingConflictCheck m : existingMeetings) {
            if (m.id().equals(meetingId)) {
                continue;
            }
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

        invitationRepository.updateStatus(meetingId, xUsername, "ACCEPTED");
    }
```

### 2. Testing
Add tests to [MeetingSchedulerIntegrationTests.java](file:///Users/mourjo/repos/prompt-meetings/src/test/java/me/mourjo/prompt/meetings/MeetingSchedulerIntegrationTests.java):
- Create calendars with different priorities (e.g. `high-priority` with 10.0, `low-priority` with 2.0).
- Create a meeting on a lower priority calendar for user Bob and accept it.
- Invite Bob to a conflicting meeting on a higher priority calendar.
- Verify Bob can accept the high priority meeting, and the low priority meeting is automatically rejected.
- Invite Bob to a conflicting meeting on a calendar with the same or lower priority.
- Verify Bob cannot accept the meeting (returns `400 Bad Request`).
