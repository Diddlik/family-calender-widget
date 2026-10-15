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
**Planning / implementation not started.** See [requirements](docs/REQUIREMENTS.md), [architecture](docs/ARCHITECTURE.md), [roadmap](docs/ROADMAP.md) and [Codex instructions](AGENTS.md).

## First milestone
A runnable Android app with calendar permission handling, calendar selection, pure testable filter engine, and a real home-screen agenda widget for upcoming matching events.

## Contributing
Use feature branches and pull requests; keep calendar event content on-device. Do not commit real calendar data, screenshots containing sensitive events, credentials, or signing keys.

## Repository
https://github.com/Diddlik/family-calender-widget
