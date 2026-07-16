# ShelfTime — context for Claude Code sessions

Standalone Wear OS client for [Audiobookshelf](https://github.com/advplyr/audiobookshelf).
Fork of [mkaflowski/ShelfTime](https://github.com/mkaflowski/ShelfTime); the upstream
maintainer has been inactive since ~September 2025, so this fork continues active
development and cherry-picks fixes from the community forks/PRs that were sitting
unreviewed upstream. See `README.md` for the user-facing feature list and changelog.

## Sandbox constraints (read this before debugging a "why won't this build" issue)

- **No local Android build.** The sandbox network policy blocks `dl.google.com`, so
  Gradle can't reach the Android Google Maven repo. Every build/test/lint must be
  verified by pushing to GitHub Actions CI — there is no way to run `./gradlew` here
  and get a real result. Push, then poll `mcp__github__actions_get` /
  `actions_list` for the run status.
- **Git can push branches but not tags.** This session's git credentials get a `403`
  on `git push origin <tag>` specifically — branch pushes work fine, tag refs don't.
  This is NOT a network/proxy issue (confirmed via the proxy status endpoint). The
  workaround already built: `.github/workflows/release.yml` has a `workflow_dispatch`
  trigger that takes a `version` input and creates+pushes the tag **from inside the
  Actions run**, using the runner's own `GITHUB_TOKEN` (a separate credential with
  `contents: write` that isn't subject to the same restriction). To cut a release:
  bump `versionCode`/`versionName` in `app/build.gradle.kts`, merge to `main`, wait for
  CI to go green, then dispatch `release.yml` with `{"version": "X.Y"}` via
  `mcp__github__actions_run_trigger` (`method: "run_workflow"`, `ref: "main"`).

## CI

`.github/workflows/build-apk.yml` runs on every push:
- `build` job: unit tests (JUnit + kotlinx-coroutines-test), JaCoCo coverage report,
  `assembleDebug`.
- `instrumented-test` job (needs `build`): boots a Wear OS emulator
  (`reactivecircus/android-emulator-runner`, api-level 33, target `android-wear`) and
  runs `connectedDebugAndroidTest`. This is a real, working Wear OS emulator test
  pipeline on GitHub-hosted runners — no self-hosted runner needed.

`.github/workflows/release.yml` — see the tag-workaround note above.

## Testing philosophy (established mid-session, keep following this)

**When making a change, add a test for it when practical, and verify via CI before
merging to `main`.** Don't ship a fix based on code-reading confidence alone if a test
is feasible.

- Pure logic (calculators, selectors, filters) → JUnit unit tests under
  `app/src/test/`. See `DownloadProgressCalculatorTest.kt`, `SmartDeleteSelectorTest.kt`
  for the existing style.
- Anything touching real Android/Media3 classes (`Uri`, `MediaItem`, `ExoPlayer`,
  Compose) → instrumented test under `app/src/androidTest/`, since the unit-test
  classpath throws on unstubbed Android framework calls and there's no Robolectric
  dependency in this project (deliberately avoided so far — adding it is a bigger
  decision, not a quick add).
- When a bug involves an Android-Service-coupled function (e.g. `PlayerService`),
  prefer extracting the pure/testable seam into its own object/class rather than
  trying to test the Service directly. See `PlayerMediaSourceBuilder` — extracted
  from `PlayerService.setAudiobook()` specifically so the "don't touch the download
  index while building media sources" invariant could be asserted by a test, and so
  it's now structurally impossible for that regression to silently come back (the
  builder has no Context/download-manager dependency to misuse in the first place).

## Known deferred / backlog items

- **`SmartDeleteManager.performSmartDelete()`** calls
  `database.libraryItemDao().getAllLibraryItems()` then filters in Kotlin for
  `.isDownloaded(context)`. A real fix would add a persisted "isDownloaded" column to
  the Room schema so this could be a single indexed SQL query — deliberately not done
  yet because it needs a Room migration, and a bad migration risks data loss that this
  sandbox can't test (no instrumented test currently exercises a DB migration path).
  Given the local `library_item` table is already scoped to locally-touched items only
  (not the whole remote library), the realistic win here is smaller than it looks —
  revisit only if this is actually measured as a hotspot.

## Release history this fork has cut

- **v1.14** — battery/optimization pass: WifiLock rescoped to only hold during active
  downloads (`MyDownloadService`), ChapterListActivity's three overlapping
  download-progress polling loops collapsed to one, PlayerActivity's position poll
  made lifecycle-aware (`repeatOnLifecycle(STARTED)`), cover art moved off the main
  thread and downsampled, `updateProgress()` swapped a full item GET for a lightweight
  `/api/me/progress/:id` call, offline progress-sync circuit breaker, `Timer` →
  coroutine for periodic saves, notification-rebuild de-dupe, Compose hygiene
  (stable `key=`, dropped a double-wrapped state).
- **v1.15** — fixed the download-progress bar freezing (Media3's
  `DownloadManager.Listener` only fires on state transitions, never byte-level
  progress — a poll loop had to come back, just consolidated into one instead of the
  original three), fixed playback stopping outright when the screen sleeps
  (`ExoPlayer.Builder()` had no wake mode at all; set `WAKE_MODE_LOCAL` — deliberately
  not `NETWORK`, since that would also hold a Wi-Fi lock that cached/downloaded
  playback doesn't need), added the sideloadable config file
  (`shelftime-config.json` in the app's external files dir, see `SIDELOADING.md`).

- **v1.16** — fixed a real slow-playback-start bug: `setAudiobook()` called
  `track.isDownloaded(this)` in the per-track loop purely to print an unused debug
  log line — that function runs a SQLite query against the Media3 download index, so
  this was N synchronous main-thread queries before playback could even begin.
  Extracted the media-source-building logic into `PlayerMediaSourceBuilder` (no
  Context dependency, so the regression can't quietly come back) with
  `PlayerMediaSourceBuilderTest` (instrumented) covering it — first time a fix in
  this fork shipped with a test proving the seam it touched, rather than
  code-reading confidence alone. Also added `FileLoggingTree` (persists Timber logs
  to the app's external files dir with size-based rotation) and `PerformanceLogger`
  (battery/memory snapshots tagged to playback start/stop and download
  Wi-Fi-lock acquire/release), so a normal day of watch use can be pulled via
  `adb pull` afterward instead of needing a live `adb logcat` session — see
  `SIDELOADING.md` under "Pulling performance logs".

## Testing note from the v1.16 fix

CI caught a real compile error on the first push of the `PlayerMediaSourceBuilder`
extraction (`artist`/`title` params declared as non-null `String` when
`LibraryItem.media.metadata.authorName`/`.title` are actually `String?`) — exactly
the kind of thing the testing-philosophy change above is meant to catch before
`main`. Worth remembering next time a change "obviously" compiles: it doesn't,
until CI says so.
