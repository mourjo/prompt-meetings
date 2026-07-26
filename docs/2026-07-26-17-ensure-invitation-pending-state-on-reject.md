# Plan: Ensure Invitation is in Pending State Before Rejecting

Ensure that a meeting invitation can only be rejected by a user if it is in the `PENDING` state. Rejecting an invitation that is already `ACCEPTED` or `REJECTED` (or `AUTO_REJECTED`) will not be allowed and will throw a `BadRequestException`.

## Proposed Changes

### 1. Core Logic Update
Modify `rejectInvite` in `MeetingService.java` to fetch the current invitation status and check if it is `PENDING`. If not, throw a `BadRequestException`.

```java
    public void rejectInvite(String xUsername, Long meetingId) {
        if (!meetingRepository.existsById(meetingId)) {
            throw new NotFoundException("Meeting not found with ID: " + meetingId);
        }

        String status = invitationRepository.getInvitationStatus(meetingId, xUsername)
                .orElseThrow(() -> new ForbiddenException("Only an invited member can respond to an existing invitation"));

        if (!"PENDING".equalsIgnoreCase(status)) {
            throw new BadRequestException("Invitation is not in a pending state");
        }

        invitationRepository.updateStatus(meetingId, xUsername, "REJECTED");
    }
```

### 2. Integration Test Adjustments
In [MeetingSchedulerIntegrationTests.java](file:///Users/mourjo/repos/prompt-meetings/src/test/java/me/mourjo/prompt/meetings/MeetingSchedulerIntegrationTests.java):
- Alice's attempt to reject her own meeting (which is `ACCEPTED` by default during creation) should now expect `400 Bad Request`.
- Assert that Alice's meeting status remains `ACCEPTED`.
- Register a new user `eve`, invite her to Alice's meeting, and verify she can reject it successfully (since her status is `PENDING`).
- Verify `eve` cannot reject it again (fails with `400 Bad Request`).
