# Plan: Move Authentication to Interceptor and Validations to Controllers

## 1. Goal
Refactor authentication checks and input validations:
- Implement a Spring MVC `HandlerInterceptor` to validate `X-USERNAME` headers globally for all protected endpoints.
- Move input parameter validations (e.g. date validation, timezone validation, default calendar check) from the Service layer to the Controller layer.
- Clean up services by removing direct parameter validations and direct `authService` invocations.

## 2. Changes
- **New Interceptor (`AuthInterceptor.java`)**:
  - Intercepts requests and extracts the `X-USERNAME` header.
  - Calls `authService.authenticate(username)`.
- **New Config (`WebMvcConfig.java`)**:
  - Registers `AuthInterceptor`.
  - Excludes the endpoints: `/users`, `/users/**`, `/swagger-ui/**`, `/v3/api-docs/**`, `/error`.
- **Update Controller (`MeetingController.java`)**:
  - In `createMeeting`, validate start/end times and timezone.
  - In `createCalendar`, validate that the calendar name is not `"default"`.
- **Update Services**:
  - In `MeetingService.java`, remove timezone/time bounds validations and `authService.authenticate` calls.
  - In `CalendarService.java`, remove `"default"` check and `authService.authenticate` calls.
  - In `UserService.java`, remove `authService.authenticate` calls if any (none exist).

## 3. Integration Tests
- Run `./mvnw clean test` to verify that global interceptor-based authentication works correctly and all validation tests pass.
