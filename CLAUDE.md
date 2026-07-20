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

- **"Continue Listening" Tile** (all 5 phases shipped): a Wear OS
  Tile (swipe over from the watch face) surfacing the same "what should I continue"
  info Book List already shows, so a book can be resumed without opening the app.
  Uses `androidx.wear.tiles`/`androidx.wear.protolayout` (Tiles 1.6.0, ProtoLayout
  Material3 1.4.0) — a new API surface for this codebase, distinct from Compose (no
  arbitrary composables, a separate declarative layout system). Required flipping
  `BookListActivity` and `PlayerActivity` to `android:exported="true"`, since a Tile's
  `LaunchAction` is fired by the system's tile-host process and needs an
  externally-launchable target activity (same requirement Android's own Tiles
  codelab has for its sample `MainActivity`) — low risk for `BookListActivity`
  specifically because it doesn't read any Intent extras; `PlayerActivity` does now
  (see phase 2 below), but only to resume playback, nothing destructive. Planned
  phases:
  1. **Shipped** — `ContinueListeningTileService`: an empty-state tile ("Open
     ShelfTime" button → `BookListActivity`), proving the tile registers/pins/binds
     end-to-end before wiring in real data.
  2. **Shipped** — real Continue Listening data: `ContinueListeningTileService` calls
     `ContinueListeningSelector.select(...)` against
     `(applicationContext as MainApp).database.libraryItemDao().getAllLibraryItems()`,
     takes the most-recent item, and shows its title/author on the tile. Tapping
     launches `PlayerActivity` with `putExtra("id", item.id)`. Two things had to
     change to make this work:
     - `TileService.onTileRequest` returns a `ListenableFuture`, not a suspend
       function, but the DB query is suspend — bridged with
       `androidx.concurrent:concurrent-futures-ktx`'s `SuspendToFutureAdapter.launchFuture { }`
       (new dependency; no prior suspend-to-`ListenableFuture` bridge existed in this
       codebase to reuse).
     - `PlayerActivity.onCreate()` previously only ever started/bound `PlayerService`
       assuming some *other* activity (`ChapterListActivity`) had already called
       `PlayerService.setAudiobook(...)` moments earlier to select the book — it never
       read any Intent extra itself. A Tile's `LaunchAction`, fired directly by the
       system, can't make that prior call. Fixed by having `PlayerActivity` check for
       an `"id"` Intent extra and call a new `PlayerService.setAudiobook(context, id,
       action = "continue")` overload (id-only, since `PlayerService.onStartCommand`
       already resolves the full `LibraryItem` from Room via that same `"id"` extra —
       no second DB query needed in `PlayerActivity`).
  3. **Shipped** — cover art, read cache-only, no live network fetch inside the Tile
     callback. This phase's original plan (reuse Coil's `ImageLoader`/cache) turned
     out to be based on a wrong assumption: cover art in this app was never actually
     cached by Coil. `BookListActivity`'s `AsyncImage` is only ever given an
     already-decoded `Bitmap` (from `ApiViewModel.coverImages`), never a URL/Uri — Coil
     never performs the fetch or touches its own disk/memory cache for covers. The
     real cache is a hand-rolled one in `ApiViewModel`/`ApiHandler`: covers are fetched
     over OkHttp from `/api/items/<id>/cover` and written to
     `context.cacheDir/<id>.jpg` (`ApiViewModel.saveBitmapToCache`). Phase 3 reads that
     same file directly (`BitmapFactory.decodeFile`), so no new dependency and no
     network call was needed — if the file isn't there yet, the tile just omits the
     icon (button falls back to text-only) rather than fetching. The bitmap is
     center-cropped to a small square and converted to raw ARGB_8888 bytes (protolayout
     resources need raw pixel data, not JPEG-encoded bytes) via
     `ResourceBuilders.InlineImageResource`/`ImageResource`, then rendered with
     `avatarImage(resource = ..., protoLayoutResourceId = ...)`. That call
     auto-registers the resource through the `ProtoLayoutScope` already threaded in via
     `materialScopeWithResources` — confirmed directly from `TileService`'s own source
     (not just its docs) that when a scope has resources, the framework bundles them
     with the tile data itself, so `onTileResourcesRequest` never needed to be
     overridden here at all.
  4. **Shipped** — keep it fresh: `PlayerService` calls a new private
     `requestContinueListeningTileUpdate()` (`TileService.getUpdater(this).requestUpdate(
     ContinueListeningTileService::class.java)`) from exactly three real state-transition
     points in its `ExoPlayer.Listener`/`onIsPlayingChanged`: pause (right after the
     existing "final save on pause" `saveProgress()` call), track change
     (`onMediaItemTransition`), and book finished (`Player.STATE_ENDED`). Deliberately
     *not* wired into `startPeriodicProgressSaving()`'s ~30s loop — that would refresh
     the tile every few seconds during active playback for no visible benefit, since the
     tile shows title/author/cover, not a live position.
  5. **Shipped** — `ContinueListeningTileServiceTest` (instrumented, same Wear OS
     emulator, no new CI infra), seeding one in-progress `LibraryItem` into Room
     (same pattern as `ScreenshotWalkTest`) and asserting the built tile's layout
     contains its title/author text. This phase's original plan (use
     `androidx.wear.tiles:tiles-testing`'s `TestTileClient`) turned out to also be
     based on a wrong assumption, same shape as Phase 3's: that library's
     `TestTileClient` is Robolectric-based under the hood (`ServiceController`/
     `shadowOf` from `org.robolectric.*`), and this project has deliberately avoided
     adding Robolectric so far (see the testing-philosophy section above) — pulling
     it in just for one test would be exactly the "bigger decision" that note warns
     against. Instead, the test drives `onTileRequest` directly: `TileService`'s
     `onTileRequest`/`onTileResourcesRequest` are `protected`, so a small test-only
     subclass (`TestableContinueListeningTileService`) widens `onTileRequest` to
     `public` (legal in Kotlin — overrides may widen but not narrow visibility) and
     exposes `attachBaseContext` (also `protected`, on `ContextWrapper`) so the test
     can attach a real target `Context` without going through the full Service
     lifecycle. The returned `Tile`'s `LayoutElement` tree (`Tile.tileTimeline` →
     `TimelineEntry.layout` → `Layout.root`) is walked recursively through
     `Box`/`Column`/`Row` looking for `Text` nodes, since Material3's `button()`
     composes an unknown-depth tree rather than a single flat element.

- **`SmartDeleteManager.performSmartDelete()`** calls
  `database.libraryItemDao().getAllLibraryItems()` then filters in Kotlin for
  `.isDownloaded(context)`. A real fix would add a persisted "isDownloaded" column to
  the Room schema so this could be a single indexed SQL query — deliberately not done
  yet because it needs a Room migration, and a bad migration risks data loss that this
  sandbox can't test (no instrumented test currently exercises a DB migration path).
  Given the local `library_item` table is already scoped to locally-touched items only
  (not the whole remote library), the realistic win here is smaller than it looks —
  revisit only if this is actually measured as a hotspot.

- **Screenshot-based documentation sprint** (shipped): auto-generates screenshots of the
  app's key screens and commits them to `docs/screenshots/` (referenced from
  `docs/SCREENS.md`), so docs stay current without manual screenshotting. Not a
  Playwright job (Playwright is browser-only, can't drive an Android/Wear OS emulator);
  `ScreenshotWalkTest` (instrumented) drives through Book List (resting + all three
  swipe-to-reveal states) → Chapter List (resting + mid-download) → Book Management
  (resting + downloaded) → Settings, via `ActivityScenario`, seeding a few realistic
  fake `LibraryItem`s into Room first (no dependency on a real Audiobookshelf server).
  Two things had to be fixed to get a real screenshot out of the headless CI emulator:
  `UiAutomation.takeScreenshot()` silently returns null on this software-rendered
  emulator (logcat showed "Failed to find ColorBuffer"), so each screen is instead
  captured by drawing the activity's own `decorView` into a `Bitmap` via `Canvas`
  directly, sidestepping the display pipeline entirely; and the app-private
  `getExternalFilesDir()` tree is invisible to `adb pull` under scoped storage even
  with root adb, so screenshots are published via `MediaStore` into a public
  `Pictures/ShelfTimeScreenshots` collection instead, which plain `adb pull` can read.
  `generate-screenshots.yml` (manual `workflow_dispatch` trigger, not on every push,
  since this doesn't assert anything — `build-apk.yml`'s regular `instrumented-test`
  job excludes `ScreenshotWalkTest` via its `notClass` filter) boots the emulator, runs
  the walk, pulls the images, and commits them straight to `docs/screenshots/`.
  Book List's swipe-to-reveal states specifically use the officially documented
  Compose/UiAutomator interop pattern (`Modifier.testTagsAsResourceId` +
  `Modifier.testTag(...)` + `UiDevice.findObject(By.res(...))` +
  `UiObject2.swipe(Direction, percent)`) rather than locating rows by title text and
  hand-computing swipe coordinates, which was unreliable across several tuning
  attempts (speed, ancestor-bounds-walking, distance, timing — see
  `ScreenshotWalkTest.swipeRowOpen()`'s doc comment for the full list) for reasons
  that never fully made sense; finding the actual swipeable element by id and using
  its own built-in gesture, calibrated against its real bounds, fixed all three states
  on the first attempt.
  - **Continue Listening Tile screenshot** (shipped, added alongside the Tile's own
    Phase 5 test-coverage pass): pinning the tile on the emulator's real tile carousel
    and automating a swipe to it would mean driving Wear OS's system UI — exactly the
    kind of fragile automation `swipeRowOpen()` above already fought through once.
    Instead, `ScreenshotWalkTest.captureContinueListeningTile()` renders the tile
    in-process: drives `ContinueListeningTileService.onTileRequest` directly (via
    `ContinueListeningTileServiceTest`'s `TestableContinueListeningTileService` double,
    reused rather than duplicated — it's `class`, not `private class`, specifically for
    this), then inflates the resulting `Layout`/`Resources` into a real `View` via the
    new `androidx.wear.protolayout:protolayout-renderer` dependency's
    `ProtoLayoutViewInstance.renderAndAttach(...)`, attached to a throwaway
    `FrameLayout` inside `BookListActivity`'s own window (any already-manifested
    Activity works as a host). `renderAndAttach()` must be called on the UI thread but
    returns a `ListenableFuture` that completes asynchronously (inflation runs in the
    background) — kicking it off inside one `ActivityScenario.onActivity { }` block and
    awaiting the future from the test thread afterwards (not inside `onActivity { }`,
    which would block the very UI thread the completion callback needs) avoids a
    deadlock; a second `onActivity { }` block then does the actual measure/layout/draw
    once inflation has genuinely finished. Saved as `05_continue_listening_tile.png`
    through the same `publishScreenshot()` helper as every other screen.

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

## Kotlin/Room versions (bumped for the Tile Phase 1 commit)

`org.jetbrains.kotlin.android` is now **2.1.20** (was 1.9.25) and `androidx.room` is
now **2.8.4** (was 2.6.1) — both had to move together. Chain of failures that forced
this, all only visible via CI (see the testing note above — same lesson, bigger
blast radius this time):
1. `androidx.wear.tiles:tiles:1.6.0` / `androidx.wear.protolayout:protolayout-material3:1.4.0`
   ship Kotlin 2.1.0 metadata; Kotlin 1.9.25's compiler can only read up to 1.9.0, so
   `kaptGenerateStubsDebugKotlin` failed outright just loading those libraries.
2. Bumping to Kotlin 2.1.20 fixed that, but moved the Compose compiler out of the
   Kotlin Gradle plugin (`composeOptions.kotlinCompilerExtensionVersion` is gone as
   of Kotlin 2.0+) — replaced by applying `org.jetbrains.kotlin.plugin.compose`
   directly in both the root and `app` `build.gradle.kts`.
3. That still didn't build: Room 2.6.1's kapt processor bundles a `kotlinx-metadata-jvm`
   that only reads metadata up to version 2.0.0, so it choked reading our *own*
   Kotlin 2.1-compiled `@Entity`/`@Dao` classes. Room 2.7.0+ explicitly targets
   Kotlin 2.0+; bumped to 2.8.4 (latest stable as of this fix).
4. `--stacktrace` had to be temporarily added to the CI `testDebugUnitTest` step to
   even see the real `Caused by:` chain for the Room/kapt failure — the default
   Gradle output only showed the generic `KaptExecutionWorkAction` wrapper. Removed
   again once diagnosed; worth re-adding first if a future kapt/Kotlin bump fails
   opaquely again.
