# Plan: Add Property-Based Tests using jqwik

## 1. Goal
Add property-based testing using `jqwik` version `1.9.3` and its Spring Boot extension `0.12.0` (`jqwik-spring`).
- Constant setup of 4 users (`user1`, `user2`, `user3`, `user4`).
- Constant setup of 3 calendars (`default`, `medium`, `high`) of priorities `1.0`, `2.0`, `3.0`.
- Actions: `create-meeting`, `invite-user`, `accept-invitation`, `reject-invitation`.
- Invariant: A user can never be in two `ACCEPTED` meetings at the same time.
- If the test fails, do not resolve the system bug; commit the failing test but ensure that it is annotated with `@Disabled` in the final code to respect the quick feedback rule, and capture the shrunk failing example in the commit message.

## 2. Changes
- **`pom.xml`**:
  - Add `jqwik` (v1.9.3) and `jqwik-spring` (v0.12.0) test dependencies.
- **`MeetingSchedulerPropertyTests.java`**:
  - Implement `@JqwikSpringSupport` property test class.
  - Implement `@BeforeTry` database cleanup and constant seeding of 4 users and 3 calendars.
  - Implement the invariant check over user meetings matching the `ACCEPTED` status.
  - Implement the jqwik `Action.Independent` actions using combinators to generate valid random inputs.
  - Disable the failing property using JUnit `@Disabled` annotation so that regular pipeline checks are not blocked, but preserve the full test code and the failure trace.
