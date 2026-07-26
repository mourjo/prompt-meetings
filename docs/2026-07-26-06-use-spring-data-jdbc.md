# Plan: Use Spring Data JDBC in MeetingRepository

## 1. Goal
Refactor `MeetingRepository` from a `JdbcTemplate`-based implementation to a Spring Data JDBC repository interface extending `ListCrudRepository<Meeting, Long>`.

## 2. Changes
- **Create Model Entity (`Meeting.java`)**:
  - Define `Meeting` POJO in `me.mourjo.prompt.meetings.model`.
  - Annotate with `@Table("meetings")` and `@Id` on the `id` field.
- **Refactor Repository (`MeetingRepository.java`)**:
  - Change `MeetingRepository` from a class to an interface extending `ListCrudRepository<Meeting, Long>`.
  - Define query methods annotated with `@Query` to fetch:
    - `Optional<String> getOrganizerUsername(Long id)`
    - `List<MeetingResponse> findMeetingsForUser(String username)`
    - `List<MeetingConflictCheck> findAcceptedMeetingsForConflictCheck(String username)`
- **Update Controller (`MeetingController.java`)**:
  - Update how a meeting is created and saved: construct a `Meeting` entity instance and call `meetingRepository.save(meeting)`.
  - Access properties using the getters of the saved entity.

## 3. Integration Tests
- Run all existing integration tests (`./mvnw clean test`) to verify that the refactored Spring Data JDBC repository behaves exactly as the previous `JdbcTemplate`-based repository.
