# ShelfTime Player Architecture Review — Sprint Plan

## Intro

This review compares ShelfTime's player, sync, and offline-download paths against the
official Audiobookshelf web player (`client/players/*`, `client/components/app/*`) and
the server API contract it talks to (`server/models/*`, `server/managers/*`), through a
Wear OS battery/CPU/network-wake lens — not a "does it look like the web client" lens.
Four parallel deep-dives fed this plan: playback engine (ExoPlayer/Media3), progress/
session sync, download/offline playback, and background-lifecycle efficiency.

Findings were deduped across passes (two passes independently found the same
`trackIndex` resume crash from different entry points — merged below), ranked by
severity × concreteness × battery/correctness impact, and cut to sprint size. This is
sized like v1.14–v1.16 (concrete, shippable fixes, not an exhaustive audit).

## Sprint backlog

### 1. Progress PATCH never sends `duration` — permanently disables server-side auto-finish

**Problem.** `ApiHandler.uploadProgress()` (ApiHandler.kt:264-275) builds its JSON body
with only `currentTime` and `lastUpdate`. This is the body used by
`ApiHandler.updateProgress()` (ApiHandler.kt:188-225), the single funnel behind
`PlayerService.saveProgress()`, `SyncWorker.doWork()`, and `syncPendingProgress()` — i.e.
every sync path in the app. `PlayerService.saveProgress()` (PlayerService.kt:305-337)
never copies the already-in-memory `audiobook.media.duration` onto
`audiobook.userProgress` before upload. Server-side, `MediaProgress.js:224-238` gates
its entire shouldMarkAsFinished heuristic on `this.duration` being truthy, and
`User.js:792-810` defaults a newly-created progress row's `duration` to `0` when the
payload omits it — which every ShelfTime PATCH does. Once a MediaProgress row's
`duration` is `0`, it stays `0` forever: no ShelfTime PATCH, on any subsequent sync, on
any device, ever includes it, so the row can never auto-finish again.
**Trigger:** any audiobook first tracked from the watch (started fresh, never opened on
web/mobile first).

**Fix.** Include `duration` (from `audiobook.media.duration`, already in memory) in the
JSON body built by `uploadProgress()`, and thread it onto `UserMediaProgress.duration`
in `saveProgress()` before calling `updateProgress()`. Consider sending `isFinished`
explicitly when remaining time is under the finish threshold rather than relying solely
on the server heuristic.

**Test plan.** Extract the JSON-body construction into its own pure function (same
extraction pattern as `PlayerMediaSourceBuilder`) and add a JUnit unit test under
`app/src/test/` asserting the built payload always carries a non-zero `duration` when
the source `LibraryItem`/`UserMediaProgress` has one.

---

### 2. `setAudiobook()` resume can throw `IndexOutOfBoundsException` at/beyond total duration

**Problem.** The trackIndex-resolution loop in `PlayerService.kt:451-460`
(`for (track in audiobook.media.tracks) { totalDuration += track.duration; if
(totalDuration > userTotalTime) break; trackIndex++ }`) never breaks or clamps when
`userTotalTime` is at or beyond the book's total duration — `trackIndex` ends up equal
to `tracks.size`, and the next line (`audiobook.media.tracks[trackIndex].startOffset`)
throws. Reached via `ChapterListActivity.kt:413-420`'s "continue" resume path, which
passes `item.userProgress.currentTime` unclamped — plausible for a finished or
near-finished book (`UserMediaProgress.kt:16` even carries `isFinished`, but nothing
checks it here). This crashes `PlayerService`, a foreground `MediaSessionService`, with
no error surfaced — a full app relaunch on Wear OS, a worse recovery than a phone/web
tab reload.

**Fix.** Clamp `trackIndex` to `tracks.lastIndex` (or coerce `userTotalTime` into range)
before the array access, mirroring the web client's own non-crashing fallback
(`LocalAudioPlayer.js:210-211`: `trackIndex >= 0 ? trackIndex : 0`). Extract the
"resolve (trackIndex, localOffsetSeconds) from (tracks, absolutePositionSeconds)"
computation into a small pure function alongside `PlayerMediaSourceBuilder` — no
Context dependency needed. This same helper is reusable for backlog item #7 below
(rewind/fast-forward track-boundary crossing), so build it generically enough to serve
both call sites now rather than twice later.

**Test plan.** JUnit unit test on the extracted function: position exactly at total
duration, position beyond total duration, position exactly at a track boundary, and the
existing in-range case — asserting a valid index/offset is always returned, never a
throw.

---

### 3. `getLibraryItems()` has no exception handling — crashes the offline-library flow

**Problem.** `ApiHandler.getLibraryItems()` (ApiHandler.kt:146-163) is the only one of
its four sibling API calls with no try/catch around the network call, response parsing,
or JSON deserialization — `getAllLibraries()`, `getCover()`, and `getItem()` all wrap
theirs. This is the exact call `ApiViewModel.loadLibraries()` (ApiViewModel.kt:226-243)
uses, invoked from `BookListActivity.kt:104` with `onlyDownloaded =
UserDataManager(this).offlineMode` — i.e. it's the path that shows the user's
*downloaded* books when offline mode is on. A Wear OS device drops connectivity (BT
relay, watch-only LTE, airplane mode while listening) far more than a phone/browser, so
this is precisely the condition under which a user opens the list to play something
already downloaded. Any `IOException`/`UnknownHostException`/`SocketTimeoutException`/
malformed JSON here propagates out of the `async{}` and crashes the app, defeating the
entire point of downloading for offline listening.

**Fix.** Wrap the network call and JSON parsing in the same try/catch pattern already
used by `getAllLibraries()`/`getItem()`/`getCover()`, returning `emptyList()` on
failure.

**Test plan.** Not directly unit-testable (real `OkHttpClient`/network). Verify via an
instrumented test using a local mock server (e.g. MockWebServer — not currently a
project dependency, so flag as a small new test-only dependency) that returns
connection-refused/timeout and asserts `getLibraryItems()` returns `[]` rather than
throwing.

---

### 4. Chapter title never updates during playback of single/few-track multi-chapter audiobooks

**Problem.** `PlayerService.updateUIMetadata()` (PlayerService.kt:398-418), which
computes `currentChapter` and broadcasts it, is only invoked from
`onMediaItemTransition`, `onMediaMetadataChanged`, and the first `STATE_READY`
transition (PlayerService.kt:230-262), plus once at Activity bind. For a single-file
audiobook (e.g. M4B) with embedded chapters, `onMediaItemTransition` never fires again
after load and `STATE_READY` effectively fires once for cached/downloaded playback (the
documented common case). Chapter title is computed once and frozen for the rest of the
book — silently defeating chapter awareness for the most common packaging format, with
no crash or log to surface it. By contrast, the web client's `currentChapter` is a
computed property re-evaluated on every `currentTime` tick
(`MediaPlayerContainer.vue:148-149`, driven by `PlayerHandler.js:270-286`'s 1s interval)
— it's always live regardless of track transitions.

**Fix.** Expose the chapter list (`audiobook.media.chapters`) from `PlayerService`, and
extract a pure `ChapterResolver.currentChapterTitle(positionSeconds, chapters):
String`, same seam-extraction pattern as `PlayerMediaSourceBuilder` (no Context/Service
dependency). Call it every tick inside `PlayerActivity`'s existing lifecycle-aware 1s
poll (PlayerActivity.kt:106-118) instead of relying only on the discrete listener
callbacks — no new wakeups, that timer already runs.

**Test plan.** JUnit unit test under `app/src/test/` for `ChapterResolver`: multiple
chapters within one track, position at exact chapter boundaries (start inclusive, end
exclusive), before-first/after-last chapter, and the existing multi-track case to
confirm no regression.

---

### 5. Every progress save does an extra GET before the PATCH — doubles the app's highest-frequency network wakeup

**Problem.** `ApiHandler.updateProgress()` (ApiHandler.kt:188-225) unconditionally calls
`getMediaProgress()` (a GET, :170-182) before every `uploadProgress()` PATCH, for every
caller — including `PlayerService.saveProgress()`'s periodic save (every 30s during
active playback, PlayerService.kt:340-360). During any continuous listening session
this doubles the highest-frequency network call in the app: GET + PATCH every ~30s
instead of one PATCH, for the whole session — a compounding radio/network-stack wake
cost, in the same category the v1.14 pass already targeted once (the full-GET →
lightweight-endpoint swap), except the lightweight endpoint is now itself called twice
per save.

**Fix.** Reserve the "is server progress newer" pre-check for paths where staleness is
actually plausible — `syncPendingProgress()` on connectivity restore and the 15-minute
`SyncWorker` pass — and skip it on the hot periodic-save path during active playback,
where the saving device is definitionally the newest-progress source.

**Test plan.** JUnit unit test on a pure policy function extracted out of
`updateProgress()` (e.g. `shouldCheckServerBeforeUpload(isPeriodicActiveSave: Boolean):
Boolean`), asserting periodic active-playback saves skip the pre-check while
background/queued-sync paths keep it.

---

### 6. `PlayerActivity` listens for a broadcast action `PlayerService` never sends (`ACTION_PAUSED` vs `ACTION_PAUSE`)

**Problem.** `PlayerService.kt:294` sends `Intent("$packageName.ACTION_PAUSE")` from
`onIsPlayingChanged(false)`. `PlayerActivity.kt:258` and `:275` register/handle
`"$packageName.ACTION_PAUSED"` — the strings never match, so this broadcast is silently
dropped on every pause not driven by the on-screen play/pause button (sleep timer
firing via `exoPlayer.pause()` at PlayerService.kt:592-596, a Bluetooth/crown
media-button pause via the MediaSession, etc.). The Activity's `isPlaying` state then
only reflects the button's own optimistic toggle, so the UI can show "playing" (live
progress ring) indefinitely while actual playback is paused — inducing exactly the kind
of extra user taps/screen-wake cycles this fork is trying to eliminate.

**Fix.** Fix the string mismatch, and hoist all four broadcast action strings
(`ACTION_PLAYING`/`ACTION_PAUSED`/`ACTION_BUFFERING`/`ACTION_UPDATE_METADATA`) into one
shared `object PlayerBroadcastActions` referenced by both files so this class of typo
can't reoccur.

**Test plan.** JUnit test under `app/src/test/` asserting every action string
`PlayerService.kt` sends is present in the `IntentFilter` `PlayerActivity.kt` builds —
plain string/constant-list comparison, no Android framework needed. Same
"structurally can't regress" spirit as `PlayerMediaSourceBuilderTest`.

---

### 7. Rewind/fast-forward don't cross track boundaries — skip near a track edge is silently truncated

**Problem.** `PlayerService.kt:513-519` implements rewind/fast-forward with the
single-argument `exoPlayer.seekTo(currentPosition ± 10000)`. Per Media3's contract, this
seeks within the *current* `MediaItem` only, clamping to `[0, currentItemDuration]` — it
does not resolve into the adjacent track. For multi-file audiobooks (MP3-per-chapter
packaging, which this codebase already handles via the multi-track `startOffset` math
in `setAudiobook()`), a 10s skip near a boundary becomes a smaller-than-requested skip
with no user feedback — easy to miss in manual testing since it only reproduces within
~10s of a boundary. The web client (`LocalAudioPlayer.js:283-300`) explicitly detects a
target time outside the current track's range and switches tracks before applying the
remaining offset.

**Fix.** Compute the target *absolute* position (`getCurrentTotalPositionInS() ± 10`)
and resolve it to `(mediaItemIndex, positionMs)` using the same track-resolution helper
proposed for backlog item #2, then call the two-argument
`exoPlayer.seekTo(mediaItemIndex, positionMs)`.

**Test plan.** JUnit test on the shared track-resolution helper: seek target 10s before
the start of track N (resolves into N-1), and 10s past the end of the second-to-last
track (resolves into the last track). Add an instrumented test if wiring the resolved
index/offset into `ExoPlayer.seekTo(index, ms)` needs real Media3 classes to assert
against.

---

### 8. `ChapterListActivity` re-queries the same track's download state 3-4x per progress event, on the main thread

**Problem.** Inside a single `downloadProgressFlow.collect` callback
(ChapterListActivity.kt:229-237), three separate full scans over
`libraryItem.media.tracks` each call `track.isDownloaded`/`track.isDownloading` per
track — and `Track.kt:26-37`'s `isDownloaded()`/`isDownloading()` are two separate
`downloadIndex.getDownload(contentUrl)` SQLite calls for the same row instead of one
lookup checked twice. A separate 2s poll loop (:248-268) calls
`MyDownloadService.getDownloadProgress()` per track again. All of this runs inside
Compose `LaunchedEffect` blocks, which default to `Dispatchers.Main.immediate` — so this
is repeated synchronous flash I/O directly on the UI thread, precisely while the
progress indicator is trying to animate. (Note: this is a different, still-open
redundancy from the one v1.14 already fixed — that pass collapsed multiple competing
poll *loops* into one; this is redundant *queries within* the surviving loop.)

**Fix.** Compute a track's download status once per tick via a single
`downloadIndex.getDownload(track.id)` call, derive `isDownloaded`/`isDownloading`/
`downloadedCount` from that one cached read, and dispatch the query off
`Dispatchers.Main` (e.g. `withContext(Dispatchers.IO)`).

**Test plan.** Extract the "given a `Download?`, compute `{isDownloaded,
isDownloading}`" mapping into a pure function (mirroring `MyDownloadService`'s private
state-mapping logic) and unit test it under `app/src/test/`. The polling/Compose wiring
itself is not worth testing directly without an androidTest harness for this activity.

## Confirmed correct, no action needed

- **No custom ExoPlayer `LoadControl`/buffering config.** `PlayerService.kt:227` uses
  Media3's `DefaultLoadControl` defaults untouched. Audio-only bitrate makes the default
  50s max buffer a trivial memory cost, and the common path is cache/download playback
  (PlayerService.kt:220-226, CacheDataSource at :469-476), not network — there's no
  wake/battery cost to tune away, and no reason to copy the web client's browser-buffering
  behavior here since the constraints aren't comparable. Worth a one-line comment near
  the `ExoPlayer.Builder` call (matching the existing `WAKE_MODE_LOCAL` comment) so a
  future pass doesn't re-flag this as "missing config."
- **`SmartDeleteManager.performSmartDelete()`'s Kotlin-side filter.** Verified
  byte-for-byte unchanged from the pattern CLAUDE.md's "Known deferred / backlog items"
  section already describes (`SmartDeleteManager.kt:33-46`). Runs on `Dispatchers.IO`,
  debounced 3s after each download — lower wearable risk than the other query-fan-out
  items in this doc. No new action; still deferred pending the Room migration decision
  already on record.
- **`WAKE_MODE_LOCAL`, WifiLock download-only scoping, notification-rebuild de-dupe.**
  `PlayerService.kt:227` (`WAKE_MODE_LOCAL`), the `lastNotifiedIsPlaying` guard
  (:127-132), and `MyDownloadService.kt`'s wifiLock acquire/release scoped to
  `STATE_DOWNLOADING` (:104-127, :214-284) were all re-verified against current code —
  none have regressed since v1.14/v1.15.
- **`PlayerActivity`'s position poll stays gated to `Lifecycle.State.STARTED`, and the
  30s progress-save cadence is not excessive vs. the web client.** The poll fully
  suspends below `STARTED` (PlayerActivity.kt:106-118) — confirmed no per-second Binder
  traffic while backgrounded. The 30s save interval (PlayerService.kt:74, 340-360) is
  actually *less* server-facing work per minute than the web client's own unconditional
  1s-tick/10s-sync interval (`PlayerHandler.js:266-287`, no visibility/lifecycle gating
  at all) — the right comparison here is protocol correctness, not literal parity, and
  ShelfTime already comes out ahead.

## Deferred / out of scope this sprint

- **No server-side `PlaybackSession` open/sync/close — listening time invisible to
  server-side stats.** Real gap (ShelfTime never calls `/api/items/:id/play`,
  `/session/:id/sync`, or `/session/:id/close`; the locally-computed
  `getTotalPlaybackTime()` at PlayerService.kt:497 is never sent anywhere), but the
  right fix is explicitly *not* to copy the web client's session lifecycle (too much
  added wake activity for this fork's battery budget) — it needs a joint client+server
  wire-format decision (the `/me/progress` PATCH endpoint has no `timeListened` field
  today). Not a quick client-only patch; revisit as its own design spike.
- **`onlyDownloaded` filtering does N per-track SQLite lookups across the entire remote
  library**, not just the locally-downloaded set (ApiViewModel.kt:234-236). Real
  efficiency gap that scales with total catalog size, but only triggers on
  library-toggle/offline-mode-switch, not a continuous hot loop like item #8 above —
  lower urgency than the main-thread-per-tick version already in the backlog.
- **Smart Delete is item-count-bounded (`maxDownloads = 5`), not byte-size-bounded.**
  Real storage-exhaustion risk (a 20+ hour audiobook can be 1-2GB; 5 long books can
  exhaust a watch's flash), but the fix needs a policy decision (byte budget source,
  free-space threshold) rather than a mechanical patch — design work, not a quick fix.
- **`PlayerActivity`'s 1Hz poll doesn't back off while paused-but-foregrounded.** Real,
  but low severity — wasted IPC/CPU only while the activity sits open and paused, a
  comparatively short-lived state; defer behind the higher-value items above.
- **Transport control taps (rewind/play-pause/fast-forward) go through
  `Intent`+`startForegroundService` instead of the already-bound `PlayerService`
  reference.** Real overhead-with-no-correctness-benefit, but taps are user-paced, not a
  hot loop — lowest absolute cost of everything found this pass.

## Verification

Every fix above ships behind CI — this sandbox cannot run `./gradlew` locally (network
policy blocks `dl.google.com`), so verification is: push the branch, poll
`actions_get`/`actions_list` for the `build-apk.yml` run (unit tests, JaCoCo, 
`assembleDebug`, then the Wear OS emulator instrumented-test job), and do not merge to
`main` until that run is green. Per this project's testing philosophy, no fix here ships
on code-reading confidence alone where a test is feasible — pure-logic extractions
(items #1, #2, #4, #5, #6, #7, #8) get JUnit coverage under `app/src/test/`; the one
finding that's inherently Android/network-coupled (#3) gets an instrumented test with a
mock server rather than being left unverified.
