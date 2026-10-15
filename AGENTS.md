# Instructions for coding agents (Codex)

Read `README.md`, `docs/REQUIREMENTS.md`, `docs/ARCHITECTURE.md`, and `docs/ROADMAP.md` before making architectural decisions.

## Mission
Build a universal Android calendar widget builder, NOT a hardcoded medical-appointments app. The first preset is for 3 family calendars with title filters for medical visits, but users must be able to reuse the core for arbitrary calendar widgets.

## Working rules
- Kotlin, Android SDK, Jetpack Compose for app UI. Prefer Jetpack Glance for widgets *after verifying current API suitability*.
- Use stable maintained dependencies compatible with the chosen SDK/AGP, pin explicit versions in version catalog, and document Java/Gradle versions.
- Preserve clean separation between Android data source, pure domain filter engine, config persistence and widget rendering.
- Prefer incremental PRs with small testable increments. Never silently skip requirements; call out deferred features.
- Do not create a remote service, OAuth flow or require INTERNET for initial version.
- Never commit real calendar data, tokens, signing keys or environment secrets.
- Do not request WRITE_CALENDAR in read-only milestone. Request READ_CALENDAR only when needed and handle denial.
- Treat filtered event titles/descriptions as private. Keep logging redacted.
- Use recurrence instances instead of simple Events queries for agenda. Test all-day, DST, timezone changes, cancelled/declined, duplicate instances and sorting.
- Every widget ID has independent selection of calendars, filters and limits.
- Refresh within platform constraints; do not promise immediate background updates.
- Don't copy source code, UI assets, branding or proprietary implementation of Your Calendar Widget. Create original design.
- If no Android SDK is available in the execution environment, explicitly report that build/device verification could not be performed.

## Definition of done
Functional code, build/test instructions, unit tests for core logic, clear docs on permissions and platform limits, no compilation warnings you can reasonably fix, and an explicit verification report.

## First task for Codex
Implement 0.1 as a PR in reviewable slices. Start with project scaffolding and a pure filter engine, then calendar provider read path, and a real homescreen widget. Scope additions to the smallest reliable vertical slice.
