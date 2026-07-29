# Prompt Meetings
This is an online API for creating and managing meetings. It allows users to register, create and manage meetings, send invitations, track responses, and coordinate calendar schedules within an organization.

Unlike traditional testing, which relies on manually writing individual test cases, this project uses property-based testing to uncover subtle bugs. Vibe-coded, this project highlights the need for property-based tests more than ever.

<p align="center">
<img src="src/test/resources/bug.png" width="600">
</p>

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

## Failing test
In this branch, the following test fails - here is an output, PBT shrinks failures so that they are self-explanatory. Auto rejections are not transitive: if a meeting X should auto-reject another meeting Y, and Y should auto-reject another meeting Z, it does not necessarily mean that X should auto reject Z.  

```
MeetingSchedulerPropertyTests.autoRejectedMeetingsHaveHigherPriorityAcceptedOverlap:130->lambda$autoRejectedMeetingsHaveHigherPriorityAcceptedOverlap$0:125 Invariant 'auto-rejected-invariant' failed after the following actions: [
    user2 creates Meeting-1 in calendar default (UTC) from 10:00 to 10:01
    user2 creates Meeting-3 in calendar medium (UTC) from 10:00 to 10:02
    user2 creates Meeting-6 in calendar high (UTC) from 10:01 to 10:02
]
final state: me.mourjo.prompt.meetings.MeetingSchedulerPropertyTests$CalendarMeetingsState@35e92a7
User user2 has an AUTO_REJECTED meeting 'Meeting-1' (priority 1.000000) but no overlapping ACCEPTED meeting with a higher priority
```


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
   rm -f .jqwik-database && ./mvnw clean test
   ```
2. When tests are executed, the system automatically overrides the database configuration to use a transient, in-memory H2 database instance (`jdbc:h2:mem:meetingsdb`). This ensures tests are isolated, fast, and do not mutate the local on-disk database.
