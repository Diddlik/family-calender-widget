# Family Calendar Widget

An Android-first, privacy-conscious, universal calendar widget builder.

## Vision
Build multiple independently configurable home-screen calendar widgets. Each widget selects calendar sources, applies reusable filter profiles, and renders an agenda. First scenario: the next 5–10 family medical appointments across three synced calendars; the architecture is **not** limited to medical appointments.

## Planned capabilities
- Read calendars already synchronized with Android (Google Calendar, CalDAV/Nextcloud via a sync adapter, etc.).
- Select one or more calendars per widget.
- Reusable, composable filter rules (AND / OR / NOT).
- Title/description/location text predicates, keyword inclusion/exclusion, date ranges, calendar membership and other supported fields.
- Chronological, scrollable agenda, widget-specific themes and item counts.
- Tap an event to open details; optional quick-create using the selected writable calendar (later milestone).
- Optional reminder profiles and notifications (later milestone).

## Status
Milestone 0.1 (read-only agenda widget) in progress. See [requirements](docs/REQUIREMENTS.md), [architecture](docs/ARCHITECTURE.md), [roadmap](docs/ROADMAP.md) and [agent instructions](AGENTS.md).

Implemented in 0.1:
- `READ_CALENDAR` request with rationale, denial and "open settings" states.
- Per-widget configuration: name shown as the widget header, calendars grouped by account (tree with select-all per account), title keywords (any match, optional medical preset), item count (1–25, default 10).
- Optional naming convention per widget: titles written as `Keyword - Name Title` (for example `Arzt - Anna Vorsorge`) are filtered by the keyword and displayed as the name plus title; other titles are shown in full.
- The app lists all placed widgets with an Edit action; widgets can also be reconfigured by tapping their header or via the launcher.
- Pure Kotlin filter engine: versioned JSON tree with ALL / ANY / NOT, calendar, text, time and all-day predicates.
- Agenda read from `CalendarContract.Instances` (recurrences expanded) over the next 366 days; cancelled, declined, finished and duplicate occurrences are dropped; stable chronological order.
- Resizable, scrollable Glance home-screen widget; tapping an event opens it in the calendar app, tapping the header reconfigures the widget.
- English and German UI, following the system language; on Android 13+ the language can also be chosen per app.

Deferred: visual nested rule editor and named reusable filter profiles (0.2), calendar writes (0.3), notifications (0.4).

## Build and test
Requirements: JDK 17 or newer (Android Studio's bundled JBR 21 works), Android SDK with platform 37. Gradle is provided by the wrapper.

| Task | Command |
| --- | --- |
| Build debug APK | `./gradlew assembleDebug` |
| Unit tests | `./gradlew testDebugUnitTest` |
| Lint | `./gradlew lintDebug` |
| Install on a device | `./gradlew installDebug` |
| Signed release bundle | `./gradlew bundleRelease` (signing via environment variables, see [release guide](docs/RELEASE.md)) |

Versions: AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.21, compileSdk/targetSdk 37, minSdk 26. All library versions are pinned in `gradle/libs.versions.toml`.

## Permissions and platform limits
- Only `READ_CALENDAR`. No `INTERNET`, no `WRITE_CALENDAR`. Event content is read on demand and never stored; only calendar IDs, filters and widget settings are persisted (app-private storage).
- Only calendars synced onto the device by an account or sync adapter are visible.
- Refresh: calendar changes trigger an update via a WorkManager content-URI trigger (a few seconds delay); time, timezone and locale changes update immediately; a 15-minute periodic job (the Android minimum) removes finished events. Android may delay background work further under battery saver or Doze, so updates are not to the second.
- All-day events are shown on their local calendar day and remain visible until that day ends. Ongoing timed events stay visible until they end.

## Manual device checklist
1. Fresh install, open the app: rationale shown; deny, then deny again: "Open app settings" appears; grant in settings and return: granted state shown.
2. Add the widget: configuration opens; no calendars selected disables Save; back cancels placement.
3. Select three calendars, apply the medical preset, save: widget lists only matching events, soonest first, at most the chosen count.
4. Add a second widget with other calendars or keywords: both widgets show their own results.
5. Recurring event: each upcoming occurrence appears once with the right date.
6. All-day event shows a date without time; timed events show date and time.
7. Edit or add a matching event in the calendar app: widget updates within seconds to minutes.
8. Wait until an event ends: it disappears within about 15 minutes.
9. Change the timezone: times update.
10. Tap an event: the calendar app opens that occurrence. Tap the header: configuration reopens with saved values.
11. Revoke calendar permission: widget shows "Tap to allow calendar access".
12. Resize the widget: the list scrolls and nothing is clipped. Check light and dark mode.

## Contributing
Use feature branches and pull requests; keep calendar event content on-device. Do not commit real calendar data, screenshots containing sensitive events, credentials, or signing keys.

## Privacy and license
Privacy policy: https://diddlik.github.io/family-calender-widget/privacy/ (source: `docs/privacy/index.html`). Licensed under the [MIT License](LICENSE).

## Repository
https://github.com/Diddlik/family-calender-widget
