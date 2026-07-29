# Prompt Meetings

## Goal
The Prompt Meetings application is a meeting scheduler and coordination platform. It allows users to register, create and manage meetings, send invitations, track responses, and coordinate calendar schedules within an organization.

## Entities
The platform defines three primary entities:
- **User**: Represented by a unique username. All scheduling operations are tied to a registered user.
- **Meeting**: A scheduled event with a title, localized start and end times, a timezone identifier, and a designated user as the Organizer (owner).
- **Invitation**: An association between a Meeting and a User representing a request for attendance. It tracks response status as either pending, confirmed (accepted), or declined (rejected). Creating a meeting automatically registers an accepted invitation for the organizer, allowing them to reject their own meeting later while others can still attend.
- **Calendar**: A global, shared schedule container identified by a unique name. Calendars have priorities represented by a floating point number. A built-in default calendar named `"default"` with priority `1.0` is always present.

## Capabilities
- **User Management**: Support for creating new users with unique usernames and retrieving the directory of all registered users.
- **Calendar Management**: Support for creating new calendars with a unique name and priority by any existing user. Retrieving a list of all global calendars.
- **Meeting Management**: Organizing new meetings by specifying the title, start/end dates, a timezone, and a target calendar name. Includes timezone-aware priority-based conflict resolution: creating a meeting automatically rejects overlapping meetings in lower-priority calendars, while preventing scheduling (returning 400 Bad Request) if a conflict exists in a calendar of the same or higher priority. Retrieving meetings associated with a user.
- **Invitation Management**: Allowing meeting organizers to invite other registered users to their meetings. Allowing invitees to accept or reject pending invitations, and enabling users to inspect their pending invitations.
- **API Documentation**: Automatic interactive documentation of the application through a Swagger/OpenAPI UI.

## Branches
The repository is designed to demonstrate property-based tests. We aim to identify test failures that would otherwise go unnoticed - the unknown unknowns. To do this, contains the following branches are used to develop the features and go one-by-one uncovering faults that went unnoticed. Move around to see the important role PBT plays:
- **[basic-features](https://github.com/mourjo/prompt-meetings/tree/basic-features)**: Contains application features before adding property-based tests.
- **[pbt-10-invariant-no-user-can-be-in-two-meetings](https://github.com/mourjo/prompt-meetings/tree/pbt-10-invariant-no-user-can-be-in-two-meetings)**: Introduces property-based tests verifying users are not in overlapping accepted meetings.
- **[pbt-20-disallow-rejection-when-accepted](https://github.com/mourjo/prompt-meetings/tree/pbt-20-disallow-rejection-when-accepted)**: Restricts accepting invitations to only those currently in a pending state.
- **[pbt-30-accept-and-auto-reject](https://github.com/mourjo/prompt-meetings/tree/pbt-30-accept-and-auto-reject)**: Implements calendar priority-based conflict resolution when users accept invitations.
- **[pbt-40-invariant-auto-rejections-should-be-justified](https://github.com/mourjo/prompt-meetings/tree/pbt-40-invariant-auto-rejections-should-be-justified)**: Distinguishes system auto-rejections and verifies they overlap with higher-priority meetings.
- **[pbt-50-rejection-allowed-only-for-pending-invites](https://github.com/mourjo/prompt-meetings/tree/pbt-50-rejection-allowed-only-for-pending-invites)**: Prevents users from rejecting invitations that are not in a pending state.

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

