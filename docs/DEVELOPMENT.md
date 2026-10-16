# Development

Technical details for contributors. The user-facing overview is in the [README](../README.md); architecture, requirements and roadmap are in this folder, release setup in [RELEASE.md](RELEASE.md).

## Build and test

Requirements: JDK 17 or newer (Android Studio's bundled JBR 21 works), Android SDK with platform 37. Gradle is provided by the wrapper.

| Task | Command |
| --- | --- |
| Build debug APK | `./gradlew assembleDebug` |
| Unit tests | `./gradlew testDebugUnitTest` |
| Lint | `./gradlew lintDebug` |
| Install on a device | `./gradlew installDebug` |
| Signed release bundle | `./gradlew bundleRelease` (signing via environment variables, see [RELEASE.md](RELEASE.md)) |

Versions: AGP 9.4.1, Gradle 9.8.1, Kotlin 2.4.21, compileSdk/targetSdk 37, minSdk 26. All library versions are pinned in `gradle/libs.versions.toml`. The app version (`versionName`) lives in `app/build.gradle.kts`; CI sets `versionCode` to the run number.

## What is implemented

- `READ_CALENDAR` request with rationale, denial and "open settings" states.
- Per-widget configuration: name shown as the widget header, calendars grouped by account (tree with select-all per account), filter rules, item count (1–25, default 10).
- Optional naming convention per widget: titles written as `Keyword - Name Title` (for example `Arzt - Anna Vorsorge`) are filtered by the keyword and displayed as the name plus title; other titles are shown in full. The name is the first word after the separator.
- The app lists all placed widgets with an Edit action; on the home screen each widget has an edit (pencil) button in its header.
- Optional reminders per widget (15 min to 1 week before; several can be combined), independent of the reminders stored in the calendar. All-day events count back from 09:00 on their day. Notifications show the event title only when the device is unlocked. Reminder style per widget: "Pop-up with sound" (channel `reminders`, high importance, heads-up) or "Silent" (channel `reminders_silent`, low importance). "Sound and vibration" opens the system settings of the pop-up channel.
- Pure Kotlin filter engine: versioned JSON tree with ALL / ANY / NOT, calendar, text, time and all-day predicates.
- Visual filter editor per widget: groups where all, at least one or no rule must match, nested up to two levels; text rules (title, description or location; contains, does not contain, starts with, is exactly, matches pattern), all-day rules and date-range rules (starts within the next 1 to 365 days; ongoing events count). Text rules without text, patterns that are invalid or longer than 200 characters, and groups left empty are dropped on save; no rules shows all events. The medical preset fills the editor with title contains Arzt / Praxis / Impfung.
- Patterns use RE2/J syntax (no backreferences or lookaround), ignore case, and run in linear time, so a pathological pattern cannot stall a refresh. The platform regex engine (ICU on Android) backtracks and cannot be interrupted, which is why it is not used.
- Filter profiles: "Save as profile" stores the current filter under a name; widgets linked to a profile share it, and saving any of them updates the profile and every linked widget. Each widget keeps a copy of the filter, so deleting a profile leaves its widgets unchanged.
- Calendar groups: "Save selection as group" stores the selected calendars under a name; applying a group replaces the selection once (no link) and skips calendars no longer on the device.
- Agenda read from `CalendarContract.Instances` (recurrences expanded) over the next 366 days; cancelled, declined, finished and duplicate occurrences are dropped; stable chronological order.
- Resizable, scrollable Glance home-screen widget; tapping an event opens it in the calendar app, where it can be edited (recurrence and organizer rules are handled there).
- A plus button in the widget header hands event creation to the calendar app (`ACTION_INSERT`); that app chooses the calendar and start time. The button is hidden when no installed app handles the intent (checked through a `<queries>` entry).
- Appearance per widget: theme (system, light, dark; wallpaper colors on Android 12+, applied on the next refresh after a wallpaper change), background opacity (100/75/50/25 %), layout (list, or grouped by day with Today/Tomorrow headers), density (comfortable, or compact with time and title on one line) and accent color for day headers and highlighted names (system or one of five fixed colors with a light and a dark tone, each at least 4.5:1 against the default widget background). If the calendar provider fails, the widget shows an error with tap-to-retry.
- English and German UI, following the system language; on Android 13+ the language can also be chosen per app. Dates use the app language, also when formatted in the background.

Deferred: direct calendar writes with `WRITE_CALENDAR` (0.3; creating and editing go through the calendar app).

## Permissions and platform limits

- `READ_CALENDAR`; `POST_NOTIFICATIONS` (Android 13+, requested only when a reminder is turned on); `SCHEDULE_EXACT_ALARM` (optional, user-granted "Alarms & reminders"; without it reminders may arrive up to 10 minutes late). No `INTERNET`, no `WRITE_CALENDAR`. Event content is read on demand and never stored; only calendar IDs, filters and widget settings are persisted (app-private storage).
- Only calendars synced onto the device by an account or sync adapter are visible.
- Refresh: calendar changes on the device trigger an update via a WorkManager content-URI trigger (a few seconds delay); time, timezone and locale changes update immediately; a 15-minute periodic job (the Android minimum) removes finished events and catches up missed reminders. Changes made elsewhere (for example on the web) appear once the account has synced them to the device. Android may delay background work further under battery saver or Doze, and a force stop pauses refresh and reminders until the app is opened again.
- All-day events are shown on their local calendar day and remain visible until that day ends. Ongoing timed events stay visible until they end.

## Manual device checklist

1. Fresh install, open the app: rationale shown; deny, then deny again: "Open app settings" appears; grant in settings and return: granted state shown.
2. Add the widget: configuration opens; no calendars selected disables Save; back cancels placement.
3. Select three calendars, apply the medical preset, save: widget lists only matching events, soonest first, at most the chosen count.
4. Add a second widget with other calendars or filter rules: both widgets show their own results.
5. Recurring event: each upcoming occurrence appears once with the right date.
6. All-day event shows a date without time; timed events show date and time.
7. Edit or add a matching event in the calendar app: widget updates within seconds to minutes.
8. Wait until an event ends: it disappears within about 15 minutes.
9. Change the timezone: times update.
10. Tap an event: the calendar app opens that occurrence. Tap the pencil in the header: configuration reopens with saved values.
11. Revoke calendar permission: widget shows "Tap to allow calendar access".
12. Resize the widget: the list scrolls and nothing is clipped. Check light and dark mode.
13. Turn on a 15-minute reminder (allow notifications), add a matching event starting in 16 minutes: a notification appears about a minute later; the lock screen shows only "Upcoming event". Without "Alarms & reminders" the configuration shows a hint and the reminder may come up to 10 minutes late.
14. Appearance: set the theme to Dark while the system is light (and the reverse): the widget follows the setting, not the system. Background 25 %: the wallpaper shows through and the text stays readable. "By day": Today/Tomorrow/date headers with times only; all-day events show "All day". "Compact": time and title on one line. Accent Green: day headers and highlighted names turn green in light and dark theme.
15. Filter editor: add a text rule "Title contains Arzt" and a group "No rule may match" with "Title contains abgesagt", save: only matching, not cancelled events show. Reopen: the same rules appear. Add a text rule without text and save: it is ignored. Delete all rules: all events show.
16. Pattern rule `^(arzt|praxis)`: matching titles show regardless of case. Type `(`: the field shows "Invalid pattern". Date range "Starts within 7 days": only events of the next week show.
17. Save a filter as profile in widget A, link widget B to it, change the filter in B and save: A shows the new results too. Delete the profile: both widgets keep their filter.
18. Save a calendar selection as group, open another widget, apply the group: exactly those calendars are selected.
19. Tap the plus in the widget header: the calendar app opens a new event; after saving a matching event the widget shows it.
20. Set a widget to "Silent" reminders: the reminder appears in the shade without sound or pop-up. "Sound and vibration" opens the channel settings.

## Contributing

Use feature branches and pull requests; keep calendar event content on-device. Do not commit real calendar data, screenshots containing sensitive events, credentials, or signing keys. Agent and project rules: [AGENTS.md](../AGENTS.md).
