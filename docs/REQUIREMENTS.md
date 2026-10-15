# Product requirements

## Goals
A universal configurable Android calendar widget builder. The family medical widget is the **first preset only**. Multiple widgets, each with independent sources, filter profile, layout, and max items. Support future event creation/editing in a chosen writable calendar.

## User stories
1. As a user, I can grant/revoke READ_CALENDAR and see an informative empty/permission state.
2. I can enumerate on-device calendars, identify account/display name, color, and choose several sources for a widget. Do not assume all family calendars are Google calendars; they must first be synced onto Android.
3. I can create and save a filter profile. Rules support nested ALL/ANY/NOT groups, and individual predicates. Profiles can be reused by many widgets.
4. I can pick keyword contains / excludes, exact phrase, prefix, and optional regular expression (with safe execution and validation); text fields include title, description, and location. Matching supports case-insensitive Unicode by default. Missing fields never crash filtering.
5. I can constrain by calendar, start/end time and future status. Exclude declined/cancelled items where observable, and provide a clear documented policy for all-day/ongoing events.
6. I can display the next N matching occurrences in chronological order (default 10), with date/time/title and calendar color, in a scrollable resizable home-screen widget.
7. Past items disappear after time advances and new ones appear, without the user reopening the app. Widget instances refresh on relevant calendar changes, date/time or timezone changes, widget configuration changes, and a reasonable periodic fallback. Observe Android battery/background constraints; do not promise second-accurate widget refresh.
8. Tapping an event opens it using an Android calendar/event intent where available. Widget configure action opens settings for that instance.
9. I can create two different widgets with different profiles simultaneously without overwriting one another.
10. I can use the app offline. No external backend or Google OAuth required for the on-device mode.

## Future user stories (not MVP)
- Event creation: quick-add form, target writable calendar, date, timezone, duration, location; either ACTION_INSERT handoff or direct CalendarContract writes after explicit WRITE_CALENDAR permission. Confirm successful insert; handle read-only calendars.
- Event editing/deletion; recurring series vs a single occurrence; invitations/attendees subject to provider semantics.
- Reminders and heads-up notifications subject to Android notification permission, notification channels and exact alarm restrictions.
- Optional additional remote calendar providers behind an adapter interface; theme/layout presets; export/import profile definitions.

## Filter representation
Use a stable versioned JSON-serializable tree: Group(AND|OR|NOT, children) and Predicate(field, operator, value). Define strict semantics for NOT (exactly one child), empty groups, missing values, empty query strings, date comparison, invalid regex, and filter migration. Keep domain engine free of Android dependencies. Regular expressions are optional and must be bounded/guarded against pathological patterns (or omitted from the first milestone).

### Initial operators
- calendarId IN set
- title/description/location CONTAINS / NOT_CONTAINS / STARTS_WITH / EQUALS
- start AFTER_NOW / BETWEEN instants
- allDay IS_TRUE / IS_FALSE
Add attendee and event color predicates only after checking actual availability in Android calendar provider, with graceful unsupported behavior.

## Privacy / permissions
- Request calendar access only when needed. No analytics, advertising, telemetry or server transmission by default.
- Calendar events may be sensitive. Never log raw event titles/descriptions in release builds.
- Avoid storing full event content in app persistence; persist filter profiles, selected calendar IDs and widget configs. Use application-private storage.
- Minimum permissions. READ_CALENDAR for MVP, no INTERNET unless a feature needs it. WRITE_CALENDAR only for explicit later edits.

## Acceptance criteria for MVP
- Android project builds with documented SDK, Java and Gradle versions.
- First-run permission denial/grant flows are robust.
- Multiple calendars can be selected and saved.
- A preset filter combining three calendars and title containing 'Arzt' OR 'Praxis' OR 'Impfung' works.
- Correct upcoming recurring instances using CalendarContract.Instances; no duplicate occurrence, chronological stable sorting; timezone and all-day correctness.
- Each widget can have independent configuration; at least two widgets with distinct profile/calendars show distinct results.
- Configured list updates after a calendar edit and after an event passes, within platform constraints.
- Pure Kotlin tests cover tree logic, time filtering, inclusions/exclusions, Unicode, nulls and ordering; manual device test checklist covers widget rendering and refresh.
- No claims of Android compatibility or successful tests without running those checks.
