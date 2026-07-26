# Plan: Implement Meeting Scheduler REST API

## 1. Goal
Implement a REST API for creating/managing meetings, user management, and invitations using Spring Boot, Spring Data JDBC, H2, Flyway, and Springdoc OpenAPI.

## 2. Requirements & Endpoints
- **User Management**:
  - `POST /users` - Create a user with a unique username (No authentication header).
  - `GET /users` - View all users in the system (No authentication header).
- **Meeting Management** (requires `X-USERNAME` header):
  - `POST /meetings` - Create a meeting with a start time, end time, timezone, and title. Organizer is the owner (from header).
  - `GET /meetings` - View all meetings of the current user (where they are organizer, or confirmed/rejected invitee).
- **Invitation Management** (requires `X-USERNAME` header):
  - `POST /meetings/{meetingId}/invites` - Invite a user by username. Only meeting organizer can invite.
  - `POST /meetings/{meetingId}/invites/accept` - Accept an invite. Only the invited member can respond.
  - `POST /meetings/{meetingId}/invites/reject` - Reject an invite. Only the invited member can respond.
  - `GET /users/{username}/invitations/pending` - View pending invitations for a user (who invited, meeting name, duration).
  - `GET /meetings/invitations/pending` - View pending invitations received by the current user.

## 3. Technology & Architecture
- **Language & Framework**: Java 25, Spring Boot 4.1.0
- **Database**: H2
  - Production/Dev config: Stored on disk (`jdbc:h2:file:./data/meetingsdb`).
  - Test config: Stored in memory (`jdbc:h2:mem:meetingsdb`).
- **Migrations**: Flyway (`src/main/resources/db/migration/V1__init.sql`).
- **Swagger**: `springdoc-openapi-starter-webmvc-ui` for OpenAPI documentation.
- **DTOs**: Java records for all input and output payloads.
- **Authentication**: Custom Spring interceptor or filter that reads `X-USERNAME` and stores it in request context or thread local.

## 4. Proposed Database Schema
- `users`:
  - `username` (VARCHAR, Primary Key)
- `meetings`:
  - `id` (BIGINT AUTO_INCREMENT Primary Key)
  - `title` (VARCHAR NOT NULL)
  - `start_time` (TIMESTAMP NOT NULL)
  - `end_time` (TIMESTAMP NOT NULL)
  - `timezone` (VARCHAR NOT NULL)
  - `organizer_username` (VARCHAR NOT NULL, Foreign Key to `users`)
- `invitations`:
  - `id` (BIGINT AUTO_INCREMENT Primary Key)
  - `meeting_id` (BIGINT NOT NULL, Foreign Key to `meetings`)
  - `invitee_username` (VARCHAR NOT NULL, Foreign Key to `users`)
  - `status` (VARCHAR NOT NULL, e.g., 'PENDING', 'ACCEPTED', 'REJECTED')
  - Unique constraint on `(meeting_id, invitee_username)`

## 5. Development Steps
1. **Dependencies**: Add Flyway (`flyway-core`, `flyway-database-h2`) and Springdoc OpenAPI UI to `pom.xml`.
2. **Properties**: Configure dev/test properties.
3. **Database Migration**: Create `V1__init.sql` for the database schema.
4. **Entities**: Define simple Java classes or records for database objects.
5. **Repositories/Data Access**: Use `JdbcTemplate` or Spring Data JDBC repositories for simple CRUD operations.
6. **Authentication/Authorization**: Create a custom handler interceptor or filter to check `X-USERNAME` header for meeting-related requests, validating that the user exists.
7. **DTOs**: Write Java records for all request and response payloads.
8. **Controller**: Implement `UserController` and `MeetingController` containing all endpoints.
9. **Tests**: Write integration tests under `src/test/java` to cover all scenarios using `MockMvc`.
10. **Documentation**: Update the `README.md` file with a description of capabilities, build instructions, how to run, and how to test.
