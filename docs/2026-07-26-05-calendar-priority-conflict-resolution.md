# Plan: Calendar Priority-Based Conflict Resolution

## 1. Goal
Implement timezone-aware conflict resolution when creating a meeting. Overlapping meetings are compared by their calendar priorities:
- If a conflict originates from a calendar with a **lower** priority, the conflicting meeting is automatically rejected by the user.
- If a conflict originates from a calendar with the **same or higher** priority, the new meeting is rejected with a `400 Bad Request` error.

## 2. Changes
- **New DTO (`MeetingConflictCheck.java`)**:
  - A Java record holding ID, title, start/end times, timezone, calendar name, and calendar priority.
- **Calendar Repository (`CalendarRepository.java`)**:
  - Add a `getPriority(String name)` method to retrieve a calendar's priority.
- **Meeting Repository (`MeetingRepository.java`)**:
  - Add a `findAcceptedMeetingsForConflictCheck(String username)` method to fetch all accepted meetings of a user along with their calendar priority.
- **Meeting Controller (`MeetingController.java`)**:
  - In `createMeeting`, retrieve all accepted meetings for the organizer.
  - Perform a timezone-aware overlap check (converting `LocalDateTime` + `ZoneId` to a normalized `ZonedDateTime` in Java).
  - Inspect all overlapping meetings:
    - If any overlapping meeting has a calendar priority `>=` the new calendar's priority, throw `BadRequestException`.
    - If all overlapping meetings have a lower calendar priority, reject those meetings by updating their invitation status to `'REJECTED'`.
  - Save and create the new meeting as usual.

## 3. Integration Tests
- Verify timezone-aware overlap comparisons (e.g. 10:00 Europe/Paris overlaps with 09:00 UTC).
- Test scheduling a meeting with the same priority (should return 400).
- Test scheduling a meeting with a lower priority than a conflicting meeting (should return 400).
- Test scheduling a meeting with a higher priority than a conflicting meeting (should succeed, and automatically reject the conflicting meeting).
