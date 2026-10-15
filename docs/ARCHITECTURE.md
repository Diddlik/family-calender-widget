# Architecture

## Principles
Offline-first, on-device calendar integration, privacy by design. Separate provider access, normalized event occurrences, filtering, widget presentation and future writes. Avoid premature multi-module Gradle complexity: begin with a well-organized single app module and clean packages; modularize when beneficial.

## Components
- `calendar/CalendarSourceRepository`: available calendars via CalendarContract.Calendars and expanded occurrences via CalendarContract.Instances. Calendar permissions, CalendarContract query columns, cancellation and pagination/chunking.
- `domain/EventOccurrence`: stable event ID, occurrence begin/end, title, description, location, all-day, calendar ID, display color, provider flags. Do not conflate event ID with recurrence instance identity.
- `filter/FilterEvaluator`: pure Kotlin evaluator of versioned predicate tree. Inject clock and ZoneId for deterministic tests.
- `profiles/`: persistent filter profiles, editing and migrations.
- `widgets/`: map Android widget ID -> configuration (sources, filter profile ID, item limit, presentation settings), rendering and update scheduler.
- `ui/`: Compose screens for onboarding, calendar picker, filter profile management and widget configuration.
- `events/`: an abstraction for future event create/edit actions. MVP supports read and opening events, NOT writes.

## Android implementation notes
- Read expanded instances in a bounded rolling window; CalendarContract.Instances is important for recurring events. Ensure projections/URI querying use the documented contract. Use a fixed test clock and timezone to verify all-day events. Restrict requests to necessary columns and time windows.
- Jetpack Glance is preferred if its current version supports the intended scrollable widget list and interactions; verify against SDK/current official docs. If limitations arise, use RemoteViews/RemoteCollectionItems or a RemoteViewsService as appropriate. Record the tradeoff in an ADR.
- Widget refresh may use calendar ContentObserver while process is alive, broadcast triggers for date/time/timezone or package-relevant changes where allowed, explicit user refresh, and WorkManager as a fallback. Follow minimum periodic interval and OS execution constraints. Avoid polling each minute.
- Widget configuration keyed by `appWidgetId`; clean on deletion and handle backup/restore/remapping carefully.
- Use Room for relational shared configurations if justified, otherwise DataStore with a serialized model and migration plan. Preferences DataStore alone is not a relational database.
- Handoff for event create via ACTION_INSERT initially; direct writes must check calendar writable access and handle sync-provider-specific restrictions.

## Testing
Unit tests of pure filter evaluation; repository integration tests with fake source or Robolectric where practical; Android instrumentation test for widget settings; manual device checklist for Android launcher and calendar integration. No private event data in fixtures.

## First delivery boundaries
Do not build a full calendar grid or custom cloud sync. Build a reliable agenda widget and a reusable filtering foundation first.
