# Plan: Refactor Property Test to Use BASE_TIME and Offset/Duration

## 1. Goal
Refactor the stateful property test to make time parameters cleaner and failure traces more concise:
- Add a constant `BASE_TIME = LocalDateTime.of(2026, 8, 1, 10, 0)`.
- Use Jqwik to generate random offset hours (e.g. `0`, `1`, `2`) and a duration (e.g. `1` hour).
- Compute `start = BASE_TIME.plusHours(offset)` and `end = start.plusHours(duration)`.
- Update `createMeetingAction().toString()` to format and print only the local time part (`HH:mm`) of the start and end times.

## 2. Changes
- **`MeetingSchedulerPropertyTests.java`**:
  - Define `private static final LocalDateTime BASE_TIME`.
  - Refactor `createMeetingAction()`:
    - Generate `offsetHours` from `Arbitraries.of(0, 1, 2)`.
    - Generate `durationHours` from `Arbitraries.of(1)`.
    - Modify the Transformer implementation to compute start/end using these inputs.
    - Modify `toString()` to print `start.toLocalTime()` and `end.toLocalTime()`.
