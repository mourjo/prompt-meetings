# Plan: Include Meeting Times in createMeetingAction Description

## 1. Goal
Improve the `create-meeting` action message by including the meeting's start and end times in the trace representation, making it easier to debug overlap conditions.

## 2. Changes
- **`MeetingSchedulerPropertyTests.java`**:
  - Update `createMeetingAction().toString()` to construct and print the local start and end date-times of the generated meeting.
