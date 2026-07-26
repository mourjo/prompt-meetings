# Plan: Ensure Invitation is in Pending State Before Accepting

Ensure that a meeting invitation can only be accepted if it is in the `PENDING` state. Accepting an invitation that is already `ACCEPTED` or `REJECTED` will not be allowed and will throw a `BadRequestException`.

## Proposed Changes

### 1. Core Logic Update
Modify `acceptInvite` in `MeetingService.java` to fetch the current invitation status and check if it is `PENDING`. If not, throw a `BadRequestException`.

```java
    public void acceptInvite(String xUsername, Long meetingId) {
        if (!meetingRepository.existsById(meetingId)) {
            throw new NotFoundException("Meeting not found with ID: " + meetingId);
        }

        String status = invitationRepository.getInvitationStatus(meetingId, xUsername)
                .orElseThrow(() -> new ForbiddenException("Only an invited member can respond to an existing invitation"));

        if (!"PENDING".equalsIgnoreCase(status)) {
            throw new BadRequestException("Invitation is not in a pending state");
        }

        invitationRepository.updateStatus(meetingId, xUsername, "ACCEPTED");
    }
```

### 2. Testing
Add test cases in `MeetingSchedulerIntegrationTests.java` to verify:
- An invitation in `ACCEPTED` state cannot be accepted again (throws `400 Bad Request`).
- An invitation in `REJECTED` state cannot be accepted (throws `400 Bad Request`).
