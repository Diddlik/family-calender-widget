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
- Done in 0.3.0: visual nested rule editor. Done in 0.4.0: reusable named filter profiles, calendar group shortcuts.
- Done: widget theme, background opacity, density, count, list and by-day layouts, empty/error states, accent color. Done in 0.6.0: color per person, freely adjustable background opacity. Done in 0.7.0: highlighted weekday in the list layout. Done in 0.8.0: optional greyed-out past events from today and up to 30 previous days. Done in 0.9.0: at most 3 past events, "Yesterday" header, screen-reader label for past events, optional countdown, end time and location, multi-day events on each day in the by-day layout.
- Done in 0.4.0: date-range rule (starts within N days) and regex matching with RE2/J (linear time, length limit, invalid patterns ignored).

## 0.3 Quick event creation
- Done in 0.4.0: plus button in the widget hands creation to the calendar app, which selects the writable calendar.
- Done in 0.5.0: per-widget new-event template (keyword and people) that prefills the title in the naming convention.
- Done: editing, recurrence and organizer semantics are left to the calendar app, opened by tapping an event.
- Open (needs a decision): direct CalendarContract writes behind WRITE_CALENDAR and confirmations, for example to store each person's events in their own calendar; requires privacy policy and Play data safety updates.

## 0.4 Notifications
- Done early: per-widget reminder offsets with notification channel, permission handling and exact/inexact alarms.
- Done in 0.4.0: reminder style per widget (pop-up with sound or silent, one channel each) and a shortcut to the channel's sound settings.

## Later (ideas, not scheduled)
- Settings backup via the Google account, without the `INTERNET` permission. Android Auto Backup (`allowBackup="true"` is already set) stores app files in the account's Google Drive app data, so `widget_config.json` is likely backed up today. Configurations are keyed by appWidgetId, and widgets get new IDs on a new device, so restored settings currently attach to nothing. Plan: add `dataExtractionRules`/`fullBackupContent` that back up only the configuration; when setting up a new widget, offer "Use settings of an earlier widget" from stored configs whose widget no longer exists. Optional: manual export/import as a JSON file through the system file picker (Storage Access Framework, which also reaches Google Drive). Never include event data.
- Snooze action in reminder notifications (for example "Remind me in 1 hour").
- Live preview of the widget in the configuration screen, so theme, colors, density and options show before saving.
- More layouts: a week strip with 7 columns, or a small tile with only the next event.
- Mark an event as done locally: hides it in the widget without changing the calendar, so no `WRITE_CALENDAR`.

## Suggested first Codex task
Read AGENTS.md and docs/*.md. Scaffold the buildable Android app and implement a read-only vertical slice: permission, selected calendars, a hardcoded but reusable preset filter, upcoming instances, widget and independent widget configuration. Add tests and document any unimplemented items or verification blockers. Open a PR rather than committing directly to main.
