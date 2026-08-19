# Plan: Improve Test Coverage in MeetingSchedulerIntegrationTests

This document outlines the plan to expand and improve test coverage in `MeetingSchedulerIntegrationTests` to cover all business logic paths, validation rules, authentication and authorization flows, calendar management, meeting conflict resolution, invitation lifecycle states, and exception handling.

## Problem Statement

Currently, [MeetingSchedulerIntegrationTests.java](file:///Users/mourjo/repos/prompt-meetings/src/test/java/me/mourjo/prompt/meetings/MeetingSchedulerIntegrationTests.java) has only a single large workflow test (`testCompleteWorkflow`). While it covers several happy paths, several critical edge cases, negative flows, validation error conditions, exception handling paths, and controller/service branches remain untested:
1. **User Validation**: Blank username, username length boundaries (< 3 chars, > 50 chars).
2. **Authentication / Interceptor**: Whitespace-only `X-USERNAME` header, unauthenticated requests across all secured endpoints.
3. **Calendar Management**: Creating duplicate custom calendars, validation on missing name or null priority, listing calendars sorted by priority.
4. **Meeting Validation**: Equal start and end time, missing required fields (title, timezone, calendarName, etc.).
5. **Invitation Edge Cases**:
   - Inviting user to non-existent meeting ID (404 Not Found).
   - Non-organizer attempting to invite members (403 Forbidden).
   - Inviting oneself (organizer cannot invite self - 400 Bad Request).
   - Duplicate invitations to the same user (400 Bad Request).
6. **Accept / Reject Edge Cases**:
   - Accepting / rejecting non-existent meeting ID (404 Not Found).
   - Uninvited user attempting to accept or reject an invitation (403 Forbidden).
   - Accepting / rejecting invitations when already in ACCEPTED, REJECTED, or AUTO_REJECTED state (400 Bad Request).
   - Accepting an invitation that causes conflict with higher or same priority meeting (400 Bad Request).
   - Accepting an invitation that automatically rejects a lower priority conflicting meeting (200 OK and status update to AUTO_REJECTED).
7. **Pending Invitations**:
   - Duration calculations (in minutes and formatted string).
   - Sorting order of pending invitations by start time.
   - Filtering out non-pending invitations.
8. **Exception Handling Coverage**:
   - `NotFoundException` (404 Not Found mapping in `GlobalExceptionHandler`).
   - `MethodArgumentNotValidException` (400 Bad Request with field validation errors).
   - `MissingRequestHeaderException` for required headers.

## Proposed Plan

1. **Add Database Reset Hook**:
   - Add `@BeforeEach` to reset `invitations`, `meetings`, `users`, and custom `calendars` (preserving the `default` calendar) so each test executes in isolated, predictable state.

2. **Structure Comprehensive Test Methods in `MeetingSchedulerIntegrationTests`**:
   - `testCompleteWorkflow()`: Retain and maintain the end-to-end integration workflow.
   - `testUserManagementAndValidation()`: Test user creation, duplicates, short usernames, long usernames, and listing users.
   - `testAuthenticationAndAuthorization()`: Test missing header, blank header, unauthenticated user, forbidden operations (non-organizer invite, uninvited accept/reject).
   - `testCalendarManagementAndValidation()`: Test creating calendars, duplicate calendar error, "default" override error, validation on empty name/null priority, sorting order.
   - `testMeetingValidationAndNotFound()`: Test missing fields, start time after end time, equal start and end time, invalid timezone, non-existent calendar, non-existent meeting ID (404).
   - `testSelfInvitationAndDuplicateInvitation()`: Test organizer inviting themselves and duplicate invitations.
   - `testMeetingCreationConflictResolution()`: Test conflict resolution during meeting creation (same/higher priority blocks, lower priority auto-rejects).
   - `testInvitationAcceptConflictResolution()`: Test conflict resolution when accepting invitations (same/higher priority blocks, lower priority auto-rejects).
   - `testInvitationStateTransitions()`: Test valid and invalid status transitions (PENDING -> ACCEPTED, PENDING -> REJECTED, disallow re-accepting / re-rejecting / accepting after rejection / rejecting after acceptance).
   - `testPendingInvitationsDetailsAndSorting()`: Test pending invitations format, duration calculation, and chronological sorting.

3. **Verify and Run Tests**:
   - Execute `./mvnw test -Dtest=MeetingSchedulerIntegrationTests` to ensure all tests pass and JaCoCo report reflects improved coverage.
   - Keep property-based tests skipped per project rules.

4. **Documentation and Commit**:
   - Update `README.md` if necessary according to guidelines.
   - Commit all changes with descriptive commit message.
