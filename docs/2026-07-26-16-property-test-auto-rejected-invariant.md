# Plan: Add Property-Based Test for AUTO_REJECTED Invariant

Add a new property-based test to verify the system invariant that every auto-rejected meeting of a user must overlap with at least one accepted meeting of a higher priority for that user.

## Proposed Changes

### 1. Update Property-Based Test Suite
In [MeetingSchedulerPropertyTests.java](file:///Users/mourjo/repos/prompt-meetings/src/test/java/me/mourjo/prompt/meetings/MeetingSchedulerPropertyTests.java):
- Implement `getPriority(MeetingResponse meeting)` helper method.
- Add `autoRejectedMeetingsHaveHigherPriorityAcceptedOverlap` property test method.
- The property test will check the invariant for each user after every generated state action.
- Mark the property test with `@Disabled` so it is not run during normal builds, in accordance with the feedback-speed guideline.

```java
    private double getPriority(MeetingResponse meeting) {
        Double p = calendarRepository.getPriority(meeting.calendarName());
        return p != null ? p : 1.0;
    }

    @Disabled
    @Property
    void autoRejectedMeetingsHaveHigherPriorityAcceptedOverlap(@ForAll("actions") ActionChain<CalendarMeetingsState> chain) {
        chain.withInvariant("auto-rejected-invariant", sut -> {
            for (String user : USERS) {
                List<MeetingResponse> myMeetings = meetingService.getMyMeetings(user);
                
                List<MeetingResponse> autoRejected = myMeetings.stream()
                    .filter(m -> "AUTO_REJECTED".equalsIgnoreCase(m.userStatus()))
                    .toList();
                
                List<MeetingResponse> accepted = myMeetings.stream()
                    .filter(m -> "ACCEPTED".equalsIgnoreCase(m.userStatus()))
                    .toList();

                for (MeetingResponse ar : autoRejected) {
                    double arPriority = getPriority(ar);
                    boolean foundOverlap = false;
                    for (MeetingResponse acc : accepted) {
                        if (overlaps(ar, acc) && getPriority(acc) > arPriority) {
                            foundOverlap = true;
                            break;
                        }
                    }
                    if (!foundOverlap) {
                        throw new AssertionError(
                            "User %s has an AUTO_REJECTED meeting '%s' (priority %f) but no overlapping ACCEPTED meeting with a higher priority"
                            .formatted(user, ar.title(), arPriority)
                        );
                    }
                }
            }
        }).run();
    }
```
