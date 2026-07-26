# Plan: Introduce Calendars Concept

## 1. Goal
Introduce a global `Calendar` concept with unique names and priorities. Every meeting must belong to a calendar. Seed a default calendar named "default" with priority 1.0, and migrate existing meetings to it.

## 2. Changes & Endpoints
- **Flyway Migration (`V2__add_calendars.sql`)**:
  - Create table `calendars` with `name` (VARCHAR PK) and `priority` (DOUBLE).
  - Seed row `('default', 1.0)`.
  - Alter table `meetings` to add `calendar_name` (VARCHAR, NOT NULL, DEFAULT 'default') with a foreign key constraint pointing to `calendars(name)`.
- **Calendar DTOs**:
  - `CreateCalendarRequest` (name, priority)
  - `CalendarResponse` (name, priority)
- **Updated Meeting DTOs**:
  - Update `CreateMeetingRequest` to accept `calendarName` (String).
  - Update `MeetingResponse` to include `calendarName` (String).
- **Calendar Endpoints**:
  - `POST /calendars` - Create a calendar (requires `X-USERNAME` header for any valid user).
  - `GET /calendars` - Fetch all calendars (requires `X-USERNAME` header).
- **Updated Meeting Controller**:
  - `POST /meetings` - Validates that the requested `calendarName` exists in the database.
- **Repository Changes**:
  - `CalendarRepository` to save and find calendars.
  - Update `MeetingRepository` to save and query meetings with `calendar_name`.

## 3. Integration Tests
- Verify default calendar is seeded.
- Test creating a calendar as a registered user.
- Test creating a meeting in a specific calendar.
- Verify meeting list contains the correct calendar name.
- Test validation: failing to create meeting with a non-existent calendar.
