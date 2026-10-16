# Shared Agent Instructions

This file is the canonical instruction source for Codex, Claude Code, and GitHub Copilot CLI.

## Maintenance

- Keep this file synchronized with the repository's verified behavior.
- Update this file in the same change when build commands, validation steps, architecture constraints, workflows, or conventions change.
- Remove obsolete instructions instead of appending corrections.
- Record only durable facts that are not obvious from the codebase.
- Replace an `OPEN` entry as soon as the decision is made or the fact becomes verifiable.
- Tool-specific instruction files contain only a pointer to this file and genuine tool-specific exceptions.

## First-use onboarding

If any `[TO FILL]` entry remains, complete this onboarding before implementing the user's first task:

1. Inspect the repository and determine every project fact that can be verified from existing files and commands.
2. Do not ask the user for information that can be discovered reliably from the repository.
3. Briefly present the discovered facts, then ask guided questions only for the remaining decisions or unknowns.
4. Ask one focused question or one closely related group of no more than three questions at a time. Explain why each answer matters and offer a recommended option when useful while allowing a free-form answer.
5. Cover the remaining topics in the order of the project facts list: purpose and scope, languages, runtimes and platforms, GUI, build and validation commands, environment requirements, then entry points, architecture, and generated-file constraints.
6. After the user answers, replace the applicable placeholders with concise verified facts, remove entries that do not apply, and record unresolved decisions explicitly as `OPEN` rather than inventing an answer.
7. Summarize what was written to this file, then continue with the user's original task.

If the user cannot be asked (non-interactive run, CI, issue-driven agent, subagent), fill every verifiable fact, mark the rest `OPEN`, and proceed with the task.

## Communication

- Respond to the user in the language the user writes in, including onboarding questions.
- Project artifacts stay in English as defined below.

## Implementation

- Implement only what the current requirement needs.
- Prefer editing existing code over adding files, layers, helpers, or abstractions.
- Use the standard library and existing dependencies before writing custom implementations.
- Do not add speculative extension points, configuration, parameters, or abstractions.
- Add a dependency only when it provides a concrete benefit and does not duplicate existing functionality.
- Preserve validation, security, accessibility, error handling, and data-integrity safeguards.

## Language and comments

- Use English for source code, identifiers, comments, tests, documentation, logs, and commit messages.
- Comments explain why a decision, constraint, workaround, or non-obvious trade-off exists.
- Do not comment what readable code already expresses.
- Prefer clear naming and small functions over explanatory comments.

## Git

- Commit coherent, verified units of work such as a finished feature, fix, or refactoring step, not every individual edit.
- Group related changes into one commit; keep unrelated changes in separate commits.
- Keep commit messages limited to change- and process-related content.
- Never add AI attribution or co-author trailers such as `Co-Authored-By: Copilot`, Claude, Codex, or similar, in commits or pull requests.

## GUI applications

Apply this section when the project has or adds a graphical user interface.

Required features:

- Versioning: one single source for the version number (Semantic Versioning), shown in the UI and embedded in build artifacts.
- About dialog: application name, version, copyright, license, and credits for every third-party library with its license.
- Auto-update: check for updates over HTTPS, verify the update's signature or checksum before installing, let the user postpone or disable automatic checks, and never lose unsaved data during an update.

Design:

- Plan the layout, navigation, and main user flows before implementing screens.
- Follow the platform's current design system: Fluent 2 on Windows, Apple Human Interface Guidelines on macOS and iOS, Material 3 on Android and where no platform system applies.
- Use native or toolkit-standard controls instead of custom widgets.
- Use a consistent spacing grid (multiples of 4 or 8 px), a limited type scale, and the system font unless the brand requires otherwise.
- Support light and dark mode and follow the system setting by default.
- Keep a clear visual hierarchy: one primary action per view, secondary actions visually subordinate, generous whitespace instead of borders and boxes.
- Design empty, loading, and error states; errors say what happened and what the user can do.
- Keep the UI responsive: run long operations in the background with progress indication and cancellation.
- Prefer undo over confirmation dialogs for reversible actions.
- Adapt to window sizes and display scaling (high DPI) without clipping or overlapping content.
- Accessibility: meet WCAG 2.2 AA contrast, provide full keyboard navigation with visible focus, label controls for screen readers, and respect reduced-motion settings.
- Keep animations short and purposeful.
- Keep user-facing strings out of code logic so they can be localized.
- Remember window size, position, and user preferences between sessions.

## Working method

- Inspect the relevant implementation and existing conventions before editing.
- Search for an existing implementation before creating a new one.
- Make the smallest coherent change that fully satisfies the request.
- Preserve unrelated user changes.
- Do not perform unrelated refactoring during a focused change.
- Ask before destructive, irreversible, security-sensitive, or materially out-of-scope actions.

## Verification

- Run the narrowest relevant test, build, lint, format, or executable check after the last change.
- Add or update tests for non-trivial behavior changes and bug fixes.
- Do not claim success without current verification evidence.
- Report what was verified and what could not be verified.
- Distinguish product failures from environment, permission, network, and tooling failures.

## Security

- Never commit, print, store, or document secrets, tokens, credentials, or private keys.
- Validate data at user, file, environment, process, and network boundaries.
- Preserve authentication, authorization, escaping, permission checks, and safe defaults.
- Do not weaken security controls to make tests or local execution pass.

## Project facts

- Purpose and scope: universal Android calendar widget builder, not a hardcoded medical-appointments app. The first preset covers 3 family calendars filtered by title for medical visits; the core must support arbitrary calendar widgets. Read `README.md`, `docs/DEVELOPMENT.md`, `docs/REQUIREMENTS.md`, `docs/ARCHITECTURE.md`, and `docs/ROADMAP.md` before architectural decisions. Current version: 0.4.0 (see `docs/ROADMAP.md`).
- Primary languages and runtimes: Kotlin 2.4, single `:app` module, AGP 9.4 (built-in Kotlin, no `kotlin-android` plugin), Gradle 9.8 wrapper, compileSdk/targetSdk 37, minSdk 26. Versions are pinned in `gradle/libs.versions.toml`.
- Platform-specific constraints: on-device only; no remote service, OAuth flow, or `INTERNET` permission in the initial version. Reminders use `POST_NOTIFICATIONS` and the user-granted `SCHEDULE_EXACT_ALARM` with an inexact fallback; do not add `USE_EXACT_ALARM` (restricted by Play policy). Refresh widgets within OS background limits; do not promise immediate updates. Check lint `NewApi` findings: `java.time` APIs newer than API 26 are not available without desugaring.
- GUI toolkit, update mechanism, and distribution: Jetpack Compose (Material 3) for app UI; Jetpack Glance for the widget. UI strings live in `values/strings.xml` (English) and `values-de/strings.xml` (German); add every new string to both, lint fails on missing translations. Distribution and updates: Google Play closed testing (Alpha, API track `alpha`); every push to `main` uploads a signed bundle with the release notes from `store/whatsnew/` via `.github/workflows/play-internal.yml` (setup in `docs/RELEASE.md`). The README is user-facing and non-technical; technical details belong in `docs/DEVELOPMENT.md`. Play Store updates replace an in-app updater, so the app needs no `INTERNET` permission. Application ID `io.github.diddlik.calendarwidget` is final. License: MIT (`LICENSE`).
- Build command: `./gradlew assembleDebug`
- Test command: `./gradlew testDebugUnitTest`
- Lint and format command: `./gradlew lintDebug` (no formatter configured).
- Local run command: `./gradlew installDebug`, then add the widget from the launcher.
- Required environment: JDK 17+ (Android Studio JBR 21 works; set `JAVA_HOME`), Android SDK with platform 37. If no Android SDK is available, report explicitly that build and device verification could not be performed.
- Important entry points: `ui/MainActivity` (launcher, permission, About), `widgets/WidgetConfigActivity` (per-widget configuration), `widgets/AgendaWidget` (Glance rendering), `widgets/WidgetRefresh` (receivers and WorkManager refresh), `widgets/ReminderNotifier` (reminder notifications and alarms), `filter/` and `domain/` (pure Kotlin engine with unit tests).
- Architecture constraints: keep Android data source, pure domain filter engine (no Android dependencies), config persistence, and widget rendering separate. Every widget ID has its own calendars, filters, and limits. Widget data must load inside Glance composition keyed on the refresh token (`AgendaWidget.refresh`), because `update()` on a running Glance session only recomposes. Regex filters use RE2/J for linear-time matching; do not switch to `java.util.regex`/`kotlin.text.Regex` (backtracking ICU on Android, not interruptible).
- Generated files: `build/` outputs; PNGs in `store/graphics/` are rendered from the SVG sources there (see `store/README.md`).
- Files or directories not to edit manually: `gradle/wrapper/` (regenerate with `./gradlew wrapper --gradle-version <version>`).

## Project rules

- Use stable maintained dependencies compatible with the chosen SDK/AGP and pin explicit versions in the version catalog.
- Deliver in small, testable, reviewable PR slices. Never silently skip requirements; call out deferred features.
- Never commit real calendar data, tokens, signing keys, or environment secrets.
- Do not request `WRITE_CALENDAR` in the read-only milestone. Request `READ_CALENDAR` only when needed and handle denial.
- Treat event titles and descriptions as private; keep logging redacted.
- Read agenda data from recurrence instances (`CalendarContract.Instances`), not plain Events queries. Test all-day, DST, timezone changes, cancelled/declined, duplicate instances, and sorting.
- Do not copy source code, UI assets, branding, or proprietary implementation of Your Calendar Widget. Create an original design.

## Definition of done

A change is complete when:

- The requested behavior is implemented.
- Relevant verification passes after the final edit.
- Appropriate error paths and edge cases are handled.
- Documentation and this file reflect changed behavior or workflows.
- Core logic has unit tests, and the build has no reasonably fixable compilation warnings.
- Permissions and platform limits are documented.
- No unrelated files, abstractions, or dependencies were introduced.
- Remaining limitations and unverified points are stated clearly.
