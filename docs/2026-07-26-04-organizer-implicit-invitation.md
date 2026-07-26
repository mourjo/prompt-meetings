# Plan: Organizer Implicit Accepted Invitation

## 1. Goal
Automatically create an accepted invitation for the meeting organizer when a meeting is created. This allows the organizer to later reject the invitation while others can still attend.

## 2. Changes
- **Meeting Controller (`MeetingController.java`)**:
  - In `createMeeting`, after calling `meetingRepository.save(...)`, call `invitationRepository.saveInvitation(...)` with the organizer username as the invitee.
  - Then update the organizer's invitation status immediately to `'ACCEPTED'` using `invitationRepository.updateStatus(...)`.
  - Return `"ACCEPTED"` as the `userStatus` in the `MeetingResponse` (instead of the hardcoded `"ORGANIZER"`).
- **Meeting Repository (`MeetingRepository.java`)**:
  - In `findMeetingsForUser`, simplify the SQL query: since the organizer now has an invitation row, we can fetch all meetings where the user's invitation status is `ACCEPTED` or `REJECTED`.
  - Remove the custom `CASE WHEN m.organizer_username = ? THEN 'ORGANIZER' ...` logic and instead return the status directly from the `invitations` table.

## 3. Integration Tests
- Update `MeetingSchedulerIntegrationTests.java`:
  - When a meeting is created, verify that the returned `userStatus` is `"ACCEPTED"` (instead of `"ORGANIZER"`).
  - Verify that the organizer can reject their own meeting.
  - Verify that after the organizer rejects the meeting, it shows status `"REJECTED"` in their meetings list, but remains unaffected for other invitees.
