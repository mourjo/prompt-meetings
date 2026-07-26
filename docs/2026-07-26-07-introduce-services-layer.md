# Plan: Introduce Service Layer and Decouple Repositories from DTOs

## 1. Goal
Refactor the application to enforce strict architectural layers:
- Controllers must only handle request validation and routing, delegating all business logic to Services.
- Controllers must never call repositories directly.
- Repositories must never return DTOs (like `MeetingResponse`, `CalendarResponse`, `PendingInvitationResponse`) directly. Instead, they should return entities or repo-specific projection records, and the service layer maps them to DTOs.

## 2. Changes
### Models & Projections
- Define `Calendar` model entity.
- Define `PendingInvitationInfo` record in `me.mourjo.prompt.meetings.repository`.
- Define `UserMeetingInfo` record in `me.mourjo.prompt.meetings.repository`.

### Repositories (Refactored)
- **`UserRepository.java`**: Kept as-is (does not return DTOs).
- **`CalendarRepository.java`**: Refactored to Spring Data JDBC interface: `ListCrudRepository<Calendar, String>`, returning a custom `@Query` of sorted `Calendar` entities.
- **`MeetingRepository.java`**: Update custom queries to return `UserMeetingInfo` and `MeetingConflictCheck` instead of REST DTOs.
- **`InvitationRepository.java`**: Update `findPendingInvitationsForUser` to return `PendingInvitationInfo` instead of REST DTOs.

### Service Layer (New)
- **`UserService.java`**: Performs user operations and maps to DTOs.
- **`CalendarService.java`**: Performs calendar operations and maps to DTOs.
- **`MeetingService.java`**: Performs meeting scheduling, invites, accept/reject, conflict validations, and maps database projections to DTOs.

### Controllers (Refactored)
- **`UserController.java`**: Injects `UserService`. Delegates calls to the service.
- **`MeetingController.java`**: Injects `MeetingService` and `CalendarService`. Delegates all calls to services.

## 3. Integration Tests
- Verify compilation and run `./mvnw clean test` to ensure full correctness of all features.
