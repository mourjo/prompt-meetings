# Prompt Meetings (letsmeet)

## Goal
The Prompt Meetings application is a meeting scheduler and coordination platform. It allows users to register, create and manage meetings, send invitations, track responses, and coordinate calendar schedules within an organization.

## Entities
The platform defines three primary entities:
- **User**: Represented by a unique username. All scheduling operations are tied to a registered user.
- **Meeting**: A scheduled event with a title, localized start and end times, a timezone identifier, and a designated user as the Organizer (owner).
- **Invitation**: An association between a Meeting and a User representing a request for attendance. It tracks response status as either pending, confirmed (accepted), or declined (rejected).

## Capabilities
- **User Management**: Support for creating new users with unique usernames and retrieving the directory of all registered users.
- **Meeting Management**: Organizing new meetings by specifying the title, start/end dates, and a timezone. Retrieving meetings associated with a user (both organized meetings and meetings with accepted or rejected invitations).
- **Invitation Management**: Allowing meeting organizers to invite other registered users to their meetings. Allowing invitees to accept or reject pending invitations, and enabling users to inspect their pending invitations.
- **API Documentation**: Automatic interactive documentation of the application through a Swagger/OpenAPI UI.

## How to Run the System
To run the application locally:
1. Ensure you have Java 25 or later installed.
2. Run the application from the root directory using the Maven Wrapper:
   ```bash
   ./mvnw spring-boot:run
   ```
3. Once started, you can access the interactive Swagger OpenAPI page to explore and execute operations:
   ```
   http://localhost:8080/swagger-ui/index.html
   ```
4. By default, the application runs on port `8080` and stores the database on disk in the `./data/meetingsdb` folder using H2. The database schema and structure migrations are automatically managed by Flyway.

## How to Test the System
To run the automated suite of integration and unit tests:
1. Execute the Maven test task:
   ```bash
   ./mvnw clean test
   ```
2. When tests are executed, the system automatically overrides the database configuration to use a transient, in-memory H2 database instance (`jdbc:h2:mem:meetingsdb`). This ensures tests are isolated, fast, and do not mutate the local on-disk database.
