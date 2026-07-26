This application called "prompt-meetings" is designed to be used for creating/managing meetings. create the following endpoints
- create a user with a unique username
- view all users in the system
- create a meeting with a start and end time (meeting organizer is the owner)
- invite other users by user name to a meeting
- accept an invite
- reject an invite
- view pending invitations for a user (return who invited the user, the name of the meeting and the duration)
- view all meetings (created, invite-confirmed, invite-rejected) of the current user
- view all pending invitations received by the current user


Use h2 database - create schema as necessary. The database should be stored on disk (but not when running tests). 

Do not recreate the schema everytime the app runs - add a database migration tool like flyway to manage changes to the database.

Create a swagger (openapi) page for the endpoints.
Create a readme file that describes the capabilities.

While creating a meeting, the HTTP endpoint should accept timezone as a separate field, the start and end time fields should not include a timezone.

Add user authentication to all meeting endpoints - for the sake of simplicity use a header X-USERNAME as the header that signifies the user that is making the request. Ensure that only an organizer can invite members to a meeting and that only an invited member can respond to an existing invitation.

In the endpoints, users should always be identified by their username and not their user-id.

Endpoints to create/view users should not require a username header.

Use Java records for all DTOs.

Explain in the readme how to run the system and test the system.
