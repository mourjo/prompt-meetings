# Plan: Introduce AUTO_REJECTED Status

Introduce a new status `AUTO_REJECTED` to distinguish invitations rejected automatically by the system (due to priority conflict resolution) from invitations rejected explicitly by the user (`REJECTED`).

## Proposed Changes

### 1. Update Database Queries
In [MeetingRepository.java](file:///Users/mourjo/repos/prompt-meetings/src/main/java/me/mourjo/prompt/meetings/repository/MeetingRepository.java), update the `findMeetingsForUser` query to fetch invitations with status `AUTO_REJECTED` in addition to `ACCEPTED` and `REJECTED`.

```sql
    @Query("""
        SELECT m.id, m.title, m.start_time, m.end_time, m.timezone, m.organizer_username, m.calendar_name,
               i.status AS user_status
        FROM meetings m
        JOIN invitations i ON m.id = i.meeting_id
        WHERE i.invitee_username = :username AND i.status IN ('ACCEPTED', 'REJECTED', 'AUTO_REJECTED')
        ORDER BY m.start_time ASC
        """)
```

### 2. Update Core Service Logic
In [MeetingService.java](file:///Users/mourjo/repos/prompt-meetings/src/main/java/me/mourjo/prompt/meetings/service/MeetingService.java):
- When auto-rejecting conflicting meetings in `createMeeting`, use the status `"AUTO_REJECTED"` instead of `"REJECTED"`.
- When auto-rejecting conflicting meetings in `acceptInvite`, use the status `"AUTO_REJECTED"` instead of `"REJECTED"`.
- Keep `"REJECTED"` for explicit user rejections in `rejectInvite`.

### 3. Update Integration Tests
In [MeetingSchedulerIntegrationTests.java](file:///Users/mourjo/repos/prompt-meetings/src/test/java/me/mourjo/prompt/meetings/MeetingSchedulerIntegrationTests.java):
- Update assertions where the system automatically rejects a lower priority meeting to expect `"AUTO_REJECTED"` instead of `"REJECTED"`.
- Leave the assertions where the user explicitly rejects a meeting to expect `"REJECTED"`.
