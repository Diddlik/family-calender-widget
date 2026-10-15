# Roadmap / Codex implementation slices

## 0.1 Foundation
- Scaffold Kotlin Android application, Compose UI, version catalog, Gradle wrapper and build docs.
- Runtime READ_CALENDAR permission, list synced calendars and source selector.
- Domain models, versioned filter AST with ALL/ANY/NOT and core string/time predicates, deterministic unit tests.
- Read expanded instances with CalendarContract, filter and sort.
- Create configurable Android agenda widget with up to 10 upcoming matches and event click action.
- Persist profiles/widget configs; refresh on edits/time passage with OS-safe scheduling.
- Provide manual test instructions and basic GitHub Actions Android build if feasible.

## 0.2 Filter editor and widget customization
- Visual nested rule editor, reusable named filter profiles, calendar group shortcuts.
- Widget theme, color, density, count, multiple layout presets, empty/error states.
- More advanced field operators; optional guarded regex after security/performance assessment.

## 0.3 Quick event creation
- Plus button opens activity; select writable calendar; create via system calendar handoff.
- Later direct CalendarContract write behind permission/confirmations.
- Editing, recurrence and organizer semantics carefully handled.

## 0.4 Notifications
- Done early: per-widget reminder offsets with notification channel, permission handling and exact/inexact alarms.
- Reminder profiles, notification channel/settings, permission handling, heads-up notification behavior and scheduled reminders with Android limitations.

## Suggested first Codex task
Read AGENTS.md and docs/*.md. Scaffold the buildable Android app and implement a read-only vertical slice: permission, selected calendars, a hardcoded but reusable preset filter, upcoming instances, widget and independent widget configuration. Add tests and document any unimplemented items or verification blockers. Open a PR rather than committing directly to main.
