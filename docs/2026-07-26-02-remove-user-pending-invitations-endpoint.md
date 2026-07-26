# Plan: Remove User Pending Invitations Endpoint

## 1. Goal
Remove the HTTP endpoint `GET /users/{username}/invitations/pending` from the application.

## 2. Steps
1. **MeetingController**: Remove the method `getPendingInvitationsForUser` mapped to `@GetMapping("/users/{username}/invitations/pending")`.
2. **Integration Tests**: Modify `MeetingSchedulerIntegrationTests.java` to remove assertions targeting the removed endpoint (Steps 13 and 15 of the test flow).
3. **Verification**: Run tests using `./mvnw clean test` to ensure that all compile errors are resolved and tests pass successfully.
4. **Commit**: Stage and commit the changes according to codebase guidelines.
