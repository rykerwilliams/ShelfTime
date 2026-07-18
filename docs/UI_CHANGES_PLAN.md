# ShelfTime UI Changes — Plan

Eight related changes gathered from a live testing/design session, grounded against the
current code so implementation has no surprises. Each section has: the problem as
observed, the resolved behavior we agreed on, the concrete code changes needed, open
questions still needing a decision, and a test plan (following this project's existing
JUnit-extraction/instrumented-test philosophy).

## 1. Smart book-tap navigation from the main list

**Problem.** Tapping a book in the main list (`BookListActivity`) always opens the
chapter/detail screen (`ChapterListActivity`), which always shows a big "Continue"/
"Start" button (`PlayButton`, `ChapterListActivity.kt:417-441`) that you then have to tap
*again* to actually start playback. This two-tap flow doesn't match the common
audiobook-app pattern of "tap a book, it starts playing" when you're not already
mid-book on something else.

**Resolved behavior** (three cases, based on whether the tapped book is the one
currently playing — tracked today by `PlayerService.currentlyPlayingItemId`,
`PlayerService.kt:637-639`):

| Tapped book is... | Behavior |
|---|---|
| The book currently playing | Open chapter list as today (so you can jump to a different chapter). The bottom button needs rework — see §2 — since nothing needs "continuing." |
| A different book, **and something is currently playing** | Open chapter list/detail screen as today, Start/Continue button included. This is browsing — tapping must not interrupt what's already playing. |
| Anything, **and nothing is currently playing** | Skip the detail screen entirely; jump straight into playback for the tapped book (Start vs. Continue chosen the same way it is today: `item.userProgress.currentTime > 10`). |

So the one-tap shortcut only fires in the narrowest, safest case — nothing is playing at
all. Every other tap behaves exactly as it does today.

**Code changes.**
- `BookListActivity`'s book-tap handler (wherever it currently launches
  `ChapterListActivity` unconditionally — needs locating, not yet identified by file:line
  in this pass) needs a branch: if `PlayerService.currentlyPlayingItemId == null`, call
  `PlayerService.setAudiobook(context, item, action = "continue")` and launch
  `PlayerActivity` directly instead of `ChapterListActivity`.
- No change needed to `ChapterListActivity` itself for this item — it's still reached
  normally for the other two cases.

**Test plan.** The routing decision itself (`shouldJumpStraightToPlayback(tappedItemId,
currentlyPlayingItemId): Boolean`) is pure logic — extract it and cover with a JUnit test
under `app/src/test/`: null currently-playing → true; tapped == currently-playing →
false; tapped != currently-playing (something playing) → false.

## 2. Chapter/detail screen: button semantics, delete safety, sync icon

**Problem A — misleading button.** `PlayButton` (`ChapterListActivity.kt:417-441`)
always shows "Continue"/"Start" regardless of whether this book is already playing. Per
§1, this screen is now only reached either for the currently-playing book (nothing to
continue) or a different book while something else plays (Start/Continue is correct
here).

**Resolved behavior.** When the viewed book is the one currently playing, replace the
button with something that reflects reality — e.g. no button at all (chapter rows
themselves become the only tap target, each seeking to that chapter), or a
non-actionable "Now Playing" indicator. **Open question:** which of these two you want —
flagging rather than picking for you.

**Problem B — delete has no confirmation, a large central touch target, and isn't
sleek.** `ChapterListActivity.kt:293-338` — the same `IconButton` cycles between
Download/Downloading/Delete icons, sitting prominently right under the title; tapping it
while downloaded deletes immediately (`MyDownloadService.sendRemoveDownload` for every
track) with only a toast afterward, no confirmation. Given a large audiobook can take
15+ minutes to redownload (measured this session: a 1.1GB file at ~4MB/s), an accidental
tap here is a real cost, not just an annoyance — and a big button directly under the
title reads as visually heavy regardless of the safety question.

**Resolved behavior — decided, in three parts:**

1. **Remove the button from the chapter-list header entirely.** Replace it with a small,
   passive **status indicator next to the book title** — present (e.g. a small
   checkmark) only when the book is downloaded, and *absent* (nothing rendered, not an
   empty/outline icon) when it isn't. Pure status signal, not a tap target.
2. **Download/Delete actions move to a new, dedicated screen** — reached by tapping the
   title/indicator. Keeps the chapter-list screen focused on browsing/playing; gives
   delete room to be a deliberate, full-screen action instead of something you can catch
   in passing. **Decided:** alongside Download/Delete, this screen also shows file size,
   chapter count, and author/narrator — all already available on the existing data
   model with no new API/parsing work needed: `LibraryItem.media.size` (file size, bytes
   — `Media.kt:18`), `LibraryItem.media.numChapters` (`Media.kt:15`),
   `LibraryItem.author` (already a computed property — `LibraryItem.kt:57-58`), and
   `LibraryItem.media.metadata.narratorName` (`Metadata.kt:11`). **Also decided:** show
   remaining device storage space alongside the book's own size, so there's enough
   context on this one screen to decide whether to download — see §8, which adds the
   free-space query this needs.
3. **Complementary fast path — `SwipeToReveal` on the main list rows** (`BookListActivity`),
   so Download/Delete are also reachable without opening a book at all. This is Google's
   own recommended Wear OS pattern for exactly this kind of secondary/destructive
   list-item action — confirmed via the official design guidelines
   ([Swipe to reveal | Wear | Android Developers](https://developer.android.com/design/ui/wear/guides/m2-5/components/swipe-to-reveal)),
   which explicitly prefers it over long-press for discoverability reasons. Implemented
   as `SwipeToRevealCard`/`SwipeToRevealChip` in `androidx.wear.compose.material` —
   already a dependency at the version this project pins
   (`compose-material:1.5.1`, `app/build.gradle.kts:99`), no new dependency needed.
   Swipe thresholds per the guidelines: <50% snaps back (no action), 50-75% reveals the
   action(s) and stays, >75% auto-commits the primary action. For the delete action
   specifically, the guidelines recommend an **undo** pattern over a confirm-before
   dialog: committing delete replaces it with an "Undo" chip that fades after a short
   window if not tapped — protects against mistakes without adding friction to every
   intentional swipe.

**Code changes.**
- Remove the `IconButton`/download-delete logic from `ChapterListActivity.kt:293-338`;
  keep the underlying `isDownloaded`/`isDownloading` state (still needed for the new
  status indicator and the new screen).
- Add the small conditional checkmark next to the title in `AudiobookInfo`
  (`ChapterListActivity.kt:277-286`ish), and make the title tappable to navigate to the
  new screen.
- New Activity/Compose destination for book management (Download/Delete + whatever else
  is decided), reusing the same `MyDownloadService.sendAddDownload`/`sendRemoveDownload`
  calls the current button already uses.
- `BookListActivity`: wrap each row in `SwipeToRevealChip` (or `SwipeToRevealCard`,
  depending on which the existing row composable is closer to) with Download as the
  primary action and Delete as the secondary/undo-guarded action.

**Problem C — sync icon is redundant and its meaning is ambiguous.**
`ChapterListActivity.kt:392-400` — cycles between CloudSync/CloudUpload(yellow)/Done
(plain gray checkmark). The checkmark state especially reads as "marked read/finished,"
when it's actually "already synced." It's also largely redundant: progress already syncs
via three automatic paths — the ~30s periodic save during playback
(`PlayerService.startPeriodicProgressSaving`), on network reconnect
(`PlayerService.syncPendingProgress`), and a 15-minute background pass
(`SyncWorker.kt:58`). The manual button calls the identical `ApiHandler.updateProgress()`
those already use.

**Resolved behavior — decided:** remove the manual sync button entirely. Given the three
automatic paths already cover it, its actual value was thin, and removing it frees the
space next to delete (also reducing the accidental-tap surface right next to the
now-more-careful destructive action).

**Code changes.** Remove the `IconButton` at `ChapterListActivity.kt:392-400` and its
`isSyncing`/cloud-icon state. `ApiViewModel.sync(item)` (`ApiViewModel.kt:170-181`)
becomes dead code once its only caller is gone — remove it too rather than leaving an
unused method around.

**Consequence of removal:** the `toUpload`-toggling oddity noted in `ApiViewModel.sync()`
becomes moot — it's being deleted, not fixed. No further action needed there.

**Test plan.** The undo-window state machine (committed → undo-visible → auto-completes
after timeout, or committed → undo-visible → undone) is pure UI state, testable in
isolation if extracted into a small enum/sealed-class + transition function,
JUnit-covered under `app/src/test/` — same pattern as this project's other
extracted-seam tests. The `PlayButton` rework depends on the §1 open question above.

## 3. Configurable seek-jump amounts (rewind/fast-forward)

**Problem.** `PlayerService.kt` has a single hardcoded `SKIP_SECONDS = 10.0` used for
*both* `ACTION_REWIND` and `ACTION_FAST_FORWARD` (`PlayerService.kt:513-519`ish, per the
sprint-fix pass — exact lines shifted since). Common audiobook/podcast convention (and
what you're asking for) is two independently configurable amounts — e.g. 10s back / 30s
forward — not one shared value.

**Important caveat:** I don't have access to the official Audiobookshelf **native**
mobile app's source in this session — only this fork (`rykerwilliams/shelftime`) and the
Audiobookshelf **server + web client** (`rykerwilliams/audiobookshelf`). The native
Android/iOS app is a separate, unlisted repository. So "match the Android app" here is
based on your own description plus common convention, not something I've verified
against real app code — flagging this so nothing is presented as more verified than it
is.

**Resolved behavior.**
- Two independently configurable values: jump-backward and jump-forward seconds.
  Suggested defaults (common convention — confirm or override): **10s backward, 30s
  forward**. Open question: confirm this mapping, since "±30 seconds, 10 seconds" didn't
  specify which direction gets which number.
- **Default sourced from the sideload JSON** (`shelftime-config.json`, imported by
  `ConfigFileImporter.kt`), **overridable afterward in the in-app Settings screen**
  (`SettingsActivity.kt`), matching the existing pattern for Smart Delete's max-downloads
  +/- stepper (`SettingsActivity.kt:89-131`).
- Important mechanism difference to design around: `ConfigFileImporter` today only fills
  fields that are still *empty* (credentials — set once) and **deletes the config file
  after every read** (`ConfigFileImporter.kt:66-68`). Jump-amount defaults aren't
  one-time secrets, so the import logic for these two new fields should apply whenever
  present in the file (not gated on "not already set"), while the file still gets
  deleted afterward as today. This means re-pushing the config file with new jump values
  and restarting the app is how a user would reset to a new default — the persisted
  Settings-UI value always wins once set, same as it works today for credentials vs.
  UI overrides.

**Code changes.**
- `SideloadConfig` (`ConfigFileImporter.kt:10-15`): add `jumpBackwardSeconds: Int?`,
  `jumpForwardSeconds: Int?`.
- `UserDataManager`: add `jumpBackwardSeconds: Int` / `jumpForwardSeconds: Int`
  properties (same `SharedPreferences`-backed pattern as `smartDeleteMaxDownloads`,
  `UserDataManager.kt:72`), with sensible built-in defaults (10 / 30) if never set by
  either the config file or Settings.
- `PlayerService.kt`: replace the single `SKIP_SECONDS` constant's use in
  `seekRelativeSeconds` call sites with `userDataManager.jumpBackwardSeconds` /
  `jumpForwardSeconds` read at the two call sites (`"ACTION_REWIND"` /
  `"ACTION_FAST_FORWARD"` branches).
- `SettingsActivity.kt`: add two more +/- steppers, one per direction, same pattern as
  the existing Smart Delete max-downloads control.
- `PlayerActivity.kt`: the Rewind/Fast-Forward icons (`Icons.Filled.FastRewind` /
  `Icons.Filled.FastForward`, `PlayerActivity.kt:178`, `:237`) are generic — no second
  count shown. Common convention (Apple Podcasts, most audiobook apps) overlays the
  number on the icon (Material has `Icons.Filled.Replay10`/`Forward30`-style icons for
  fixed common values, but since these are now *user-configurable*, a fixed icon set
  won't cover every value — likely need a custom icon+text overlay, e.g. the existing
  generic rewind/forward icon with the configured number rendered as a small text badge
  on top). Flagging as a design detail to settle when we get to implementation, not
  something to lock in now.

**Test plan.** `UserDataManager`'s new getters/setters are trivial
`SharedPreferences` wrappers, same as existing ones — no new test needed, consistent
with how `smartDeleteMaxDownloads` isn't separately tested. The
`ConfigFileImporter` change (apply-always vs. apply-if-empty distinction) is worth a
JUnit test given it's a real behavioral branch: config with jump values present + a
config with them absent, asserting the right one applies unconditionally and the other
doesn't touch existing prefs.

## 4. Now Playing screen: chapter-relative time display, moved and tappable

**Problem.** Today, `PlaybackControls`' chapter title sits alone at the top
(`PlayerActivity.kt:144-151`), and elapsed/total time for the **whole book** sits at the
bottom (`"${timeToString(currentPosition/1000)} / ${timeToString(duration/1000)}"`,
`PlayerActivity.kt:385`) — always book-relative, never chapter-relative, and not
interactive.

**Resolved behavior.**
- **Default display mode is time remaining in the current chapter** — stated priority:
  "usually I want to finish a chapter, so I care about how long it is." This is the
  resting state, not just one stop on a cycle.
- Move this element to the **top** of the screen, presumably alongside/replacing part of
  the chapter-title area.
- Still make it **tappable to cycle** to other modes for when book-wide progress is
  wanted — chapter-remaining (default) → chapter elapsed/total → book elapsed/total,
  matching the existing sleep-timer preset-cycling pattern already used in this file
  (`PlayerActivity.kt:426` — same `(index + 1) % modes.size` idiom) — but the cycle
  should reset to chapter-remaining on chapter change / next screen open, not persist a
  book-wide mode as the new default.

**Code changes.**
- `ChapterResolver` (`utils/ChapterResolver.kt`) currently only returns a chapter
  *title*. Add a companion function returning the full `Chapter` (which already has
  `start`/`end`/`title` — `data/Chapter.kt:6-10`) for a given position, e.g.
  `ChapterResolver.currentChapter(positionSeconds, chapters): Chapter?`, so the UI can
  compute `positionSeconds - chapter.start` (elapsed in chapter) and
  `chapter.end - positionSeconds` (remaining in chapter).
- Expose this from `PlayerService` the same way `getCurrentChapterTitle()` already is
  (`PlayerService.kt:144-152` per the earlier sprint fix) — e.g. `getCurrentChapter():
  Chapter?` — and poll it from the same existing 1s loop in `PlaybackControls`
  (`PlayerActivity.kt:107-128`) that already recomputes chapter title every tick, so this
  adds no new timer.
- New local `remember`-ed cycling-mode state in `PlaybackControls`, same pattern as
  `BottomLayout`'s `sleepTimerIndex`.
- Layout: move the time `Text` from `BottomLayout` (`PlayerActivity.kt:384-388`) up to
  sit with/near the chapter title `Box` (`PlayerActivity.kt:139-152`); `BottomLayout`
  keeps the Volume/Speed/Sleep-timer icon row only.

**Test plan.** `ChapterResolver.currentChapter()` is the same pure, no-Context function
`currentChapterTitle()` already is — extend the existing `ChapterResolverTest.kt` with
cases for elapsed/remaining-in-chapter math (position mid-chapter, at boundaries) rather
than creating a new test file.

## 5. Sleep timer: add "Sleep at end of chapter"

**Problem/request.** The sleep timer today only offers fixed-duration presets —
`sleepTimerPresetsMinutes = listOf(0, 15, 30, 45, 60)` (`PlayerActivity.kt:307`), cycled
by `BottomLayout`'s Bedtime `IconButton` (`PlayerActivity.kt:423-443`), backed by
`PlayerService.setSleepTimer(minutes)` — a plain `delay(minutes * 60_000L)` coroutine
that pauses `exoPlayer` when it elapses (`PlayerService.kt:646-654`). There's no
"stop when this chapter ends" option, which is a natural pairing with §4's
chapter-remaining focus — if you care how long is left in the chapter, "stop exactly
there" is the logical sleep-timer counterpart.

**Resolved behavior.** Add "Sleep at end of chapter" as an additional preset in the same
cycle.

**Design consideration — recompute-on-event, not continuous polling.** A delay computed
once (`chapter.end - positionSeconds`, via the same `ChapterResolver.currentChapter()`
addition from §4) and handed to the *existing* `setSleepTimer`-style
`delay(ms)` mechanism drifts whenever something breaks the assumption that wall-clock
time and playback-position time move together at a fixed rate. Concretely, four points
need a recompute-and-reschedule, not a continuous re-check loop:

1. **When the option is first selected** — the initial computation, not a recompute.
2. **On every seek while armed** — ExoPlayer already has a real event for this,
   `Player.Listener.onPositionDiscontinuity(oldPosition, newPosition, reason)`, which
   fires on rewind/fast-forward/chapter-tap seeks (this app isn't currently listening
   for it, per a scan of `PlayerService.kt:232-onward`'s existing listener — this would
   be a new callback override, not new infrastructure). No polling needed here at all.
3. **On playback speed changes while armed** — a delay computed at 1x is wrong once
   speed changes (`playerService.setSpeed()`); recompute at that call site too.
4. **On pause/resume while armed** — a plain `delay()` keeps counting in wall-clock time
   even while paused, so without handling this the timer could fire mid-chapter after a
   long pause. Cancel the scheduled delay on pause (`onIsPlayingChanged(false)`,
   `PlayerService.kt:278` — already an existing callback), recompute and reschedule on
   resume from the position at that moment — the same computation as a seek, just
   triggered by a different existing callback.

All four are discrete, already-available hooks — no new timer/poll loop needed anywhere.

**Code changes.**
- `PlayerActivity.kt:307`'s preset list gains a distinct sentinel (not another minute
  value — needs its own type, e.g. a sealed class/enum `SleepTimerOption` with
  `Off`, `Minutes(Int)`, `EndOfChapter` variants, replacing the current plain `Int`
  list) so `PlayerService` can tell "sleep in N minutes" apart from "sleep at chapter
  end."
- `PlayerService.kt`: new `setSleepTimerAtChapterEnd()` (or a unified `setSleepTimer`
  overload taking the new sealed type) that computes the initial delay and arms it;
  add `onPositionDiscontinuity` to the existing `Player.Listener` and hook the
  speed-change/pause-resume points above to recompute-and-reschedule while this mode
  is the active sleep option; a no-op for all four hooks otherwise.

**Test plan.** The delay-computation itself
(`secondsUntilChapterEnd(positionSeconds, chapterEnd, playbackSpeed): Double`) is pure
and JUnit-testable under `app/src/test/`, covering the speed-adjustment math directly.
The actual `onPositionDiscontinuity`/reschedule wiring is Android/Media3-coupled and
would need an instrumented test if one is added, following this project's usual
seam-extraction split.

## 6. Offline Mode doesn't actually go offline

**Problem.** "Offline Mode" (a checkbox on the login screen, `LoginActivity.kt:79,
102-105`, persisted as `UserDataManager.offlineMode`) sounds like it should stop the app
from attempting network calls. It doesn't. Traced through
`ApiViewModel.getLibraries()`/`loadLibraries()` (`ApiViewModel.kt:183-243`): the
`onlyDownloaded` parameter (set from `offlineMode`) only *filters* whatever comes back —
`apiHandler.getAllLibraries()` and `getLibraryItems()` are called unconditionally,
regardless of the flag. So checking "Offline Mode" doesn't skip the network attempt at
all; it just changes what's displayed once that (still-attempted, likely doomed) call
resolves or fails. The only other thing the flag currently does is suppress error toasts
(`ChapterListActivity.kt:104-105`, `LoginActivity.kt:65`) and make the login screen skip
a network login attempt if a token's already saved (`LoginActivity.kt:124-127`).

Separately, it's only settable from the Login screen — there's no way to flip it later
from the general Settings screen without going back through login.

**Resolved behavior — open question, not decided yet.** Two different concerns are
currently smushed into one boolean: (a) "don't bother attempting network calls" (a
connectivity assumption) and (b) "only show me downloaded items" (a content filter).
Worth deciding whether to:
- Make `offlineMode` actually gate the network attempt (check the flag — or real
  connectivity via `NetworkConnectivityManager`, which `PlayerService` already uses —
  before calling `loadLibraries()` at all), so checking it saves the wasted attempt/wait;
  and/or
- Split it into two settings: a content filter ("Downloaded Only") and a connectivity
  assumption, so a user who's `onlyDownloaded` but has real connectivity still gets a
  background refresh, while a user who's genuinely offline doesn't wait on a doomed call
  at all; and/or
- Move it (or a duplicate control) into `SettingsActivity` so it's not login-only.

**Code changes.** Depends on which resolution above is chosen — not locked in yet.
Minimum-viable version: in `ApiViewModel.getLibraries()`, skip the call to
`loadLibraries()` entirely when `onlyDownloaded` is true (today it always runs
regardless), which at least makes the existing flag do what its name implies.

**Test plan.** The gating decision itself is pure logic
(`shouldAttemptNetworkRefresh(offlineMode: Boolean, isNetworkAvailable: Boolean):
Boolean` or similar, depending on which resolution is chosen) — JUnit-testable under
`app/src/test/` once the exact behavior is settled.

## 7. Rotary input (bezel/crown): scrubbing vs. volume, configurable

**Problem.** The physical rotary bezel already works for scrolling the main book list
(`BookListActivity.kt:52-53, 319-322` — Wear Compose's `Modifier.rotaryScrollable`), but
it's never wired up on the Player screen, so it does nothing while listening — which is
where you'd most want it.

**Resolved behavior.** Wire the bezel to **scrubbing** by default (seek), not volume —
volume already has redundant coverage (physical hardware volume buttons plus the
existing in-app slider, `PlayerActivity.kt`'s `BottomLayout` volume `InlineSlider`), while
there's currently no fine-grained seek at all beyond the fixed jump buttons (§3). Make it
a Settings toggle (scrub / volume / off) using the same persisted-preference pattern as
the jump-seconds setting (§3), since bezel hardware presence *and* preference both vary —
not every Wear OS watch has a physical rotary bezel (confirmed: it's not universal
hardware, mainly Samsung Galaxy Watch-style devices), so this must degrade to a silent
no-op on watches without one, which the Rotary Input API already does naturally (no
events fire if there's no hardware to generate them).

**Code changes.**
- `PlayerActivity.kt`'s `PlaybackControls`: add `Modifier.rotaryScrollable(...)` (same
  API `BookListActivity` already uses) to the player screen's root/focusable container,
  routing scroll deltas to either a seek call (`ACTION_REWIND`/`ACTION_FAST_FORWARD`-style,
  or a direct `exoPlayer.seekTo` via a new PlayerService method) or the existing volume
  `AudioManager` call, based on the new setting.
- `UserDataManager`: new persisted enum/string preference (e.g. `bezelMode`:
  `Scrub`/`Volume`/`Off`), same `SharedPreferences` pattern as existing settings.
- `SettingsActivity.kt`: new control to choose the mode (a 3-way toggle or cycling
  button, consistent with existing controls in that screen).
- Sensitivity/granularity tuning (how many seconds per bezel notch) is an implementation
  detail to settle once this is being built, not before — flagging so it isn't
  forgotten, not deciding it now.

**Test plan.** Whatever seconds-per-notch mapping function is chosen (rotary delta →
seek-seconds) should be pure and JUnit-testable under `app/src/test/`, same pattern as
the other extracted seams in this project. The actual `rotaryScrollable` wiring is
Compose/Android-coupled and untestable without an instrumented test.

## 8. Byte-size-aware Smart Delete + device free space

**Problem.** Two related storage gaps, both explicitly called out already in this
project's own `CLAUDE.md` deferred-items note: Smart Delete
(`SmartDeleteManager.kt`/`SmartDeleteSelector.kt`) is purely **count**-bounded —
`userDataManager.smartDeleteMaxDownloads` (default-ish 5, per the existing Settings
stepper, `SettingsActivity.kt:89-131`) triggers deleting the oldest downloads once the
count is exceeded, regardless of how large those books actually are. A 20+ hour
audiobook can be 1-2GB; 5 such books can exhaust a watch's flash long before hitting the
item-count limit. Separately, there's currently no way to see actual device free space
anywhere in the app — relevant both to Smart Delete's own policy and to the new
management screen from §2, which needs it for informed download decisions.

**Resolved behavior.**
- **Decided: the byte budget and the existing count limit coexist — Smart Delete runs
  once *either* is exceeded**, and keeps deleting oldest-first (still never touching
  `currentlyPlayingItemId`) until *both* are back within budget, not just whichever was
  crossed first. No mode flag needed — both limits are always active together, not
  alternate modes to pick between.
  `LibraryItem.media.size` (`Media.kt:18`) already gives each book's size — summing
  it across `database.libraryItemDao().getAllLibraryItems().filter {
  it.isDownloaded(context) }` (the same query `performSmartDelete()` already runs)
  needs no new download-index I/O.
- Add a **device free-space query**, surfaced on the new management screen (§2) next to
  the book's own size, so you can see both numbers at the point of deciding whether to
  download.

**Code changes.**
- Replace `SmartDeleteSelector.selectItemsToDelete()`'s single-condition logic with one
  that walks the oldest-first, currently-playing-excluded candidate list accumulating
  both a running count and a running byte sum, adding items to the delete list until
  *both* `remainingCount <= maxDownloads` and `remainingBytes <= maxTotalBytes` hold —
  e.g. `selectItemsToDelete(downloadedItems: List<Pair<LibraryItem, Long>>,
  currentlyPlayingItemId: String?, maxDownloads: Int, maxTotalBytes: Long):
  List<LibraryItem>` (same function, extended signature, not a parallel/alternate one).
- `SmartDeleteManager.performSmartDelete()`: pass `LibraryItem.media.size` alongside the
  existing `(LibraryItem, updateTimeMs)` pairs so the extended selector has what it
  needs; pass both configured limits through.
- `UserDataManager`: new persisted byte-budget preference (same `SharedPreferences`
  pattern as `smartDeleteMaxDownloads`) — no mode flag needed, per the decision above.
- `SettingsActivity.kt`: a stepper for the byte/GB budget, same pattern as the existing
  max-downloads control.
- New small utility (e.g. `StorageUtils.getAvailableSpaceBytes(context): Long`), reading
  free space on the same volume downloads actually live on —
  `context.getExternalFilesDir(null)` with a `context.filesDir` fallback, the exact
  path `MyDownloadService.getDownloadDirectory()` already resolves
  (`MyDownloadService.kt:401-405`) — via `File.getFreeSpace()` (no special permission
  needed for the app's own volume). Format with the existing
  `DownloadProgressCalculator.formatBytes()` (`DownloadProgressCalculator.kt:77`,
  already generic B/KB/MB/GB — no new formatting code needed).

**Test plan.** The byte-budget selector is pure logic, same shape as the existing
`SmartDeleteSelectorTest.kt` — extend that file with byte-sum cases (a mix of book sizes
that exceeds the budget at different points in the oldest-first ordering) rather than a
new test file. `StorageUtils.getAvailableSpaceBytes()` wraps a real `File` I/O call —
not unit-testable the way the pure selector is; code-review confidence is reasonable
here given it's a single well-known API call, consistent with how this project treats
comparably thin wrappers elsewhere (e.g. `smartDeleteMaxDownloads`'s getter/setter isn't
separately tested either).

## Open questions to settle before implementation

1. §2: when viewing the currently-playing book's chapter list, what replaces the
   Continue button — nothing (chapters become the only tap target), or a non-actionable
   "Now Playing" indicator?
2. §3: confirm the backward/forward second-count mapping (suggested 10s back / 30s
   forward).
3. §3: icon treatment for configurable jump amounts (numbered badge over a generic
   icon, vs. something else) — not blocking, can be settled during implementation.
4. §4: confirm the secondary cycle order after chapter-remaining (suggested:
   chapter-elapsed/total → book-elapsed/total) — default mode itself is settled
   (chapter-remaining).
5. §5: confirm "Sleep at end of chapter" should sit alongside the existing minute
   presets in the same cycle (vs. a separate control) — assumed same cycle above.
6. §6: which resolution for Offline Mode's semantics — gate the network call on the
   existing flag, split it into two settings (content filter vs. connectivity
   assumption), move/duplicate it into `SettingsActivity`, or some combination?
7. §7: bezel scrub-vs-volume default and seconds-per-notch sensitivity — default
   (scrub) is settled, granularity is not.

(§2's manual sync button is also settled — remove it, per the decision above — so it's
not repeated in this list.)

## Suggested implementation order

1. §1 (main-list routing) and §2's button-semantics fix are tightly coupled (both
   depend on knowing "is this book currently playing") — do together.
2. §2's status-indicator + new management screen + sync-icon removal — independent of
   §1, can go anytime. Note the `SwipeToReveal` part of §2 touches `BookListActivity`,
   the same file §1's routing change touches — worth sequencing back-to-back with §1
   rather than as a fully separate pass, even though the two aren't logically coupled.
3. §3 (configurable jump amounts) — independent of the above, but touches
   `PlayerService.kt` and `PlayerActivity.kt` alongside §4/§5/§7, so sequence them
   together to avoid the same kind of overlapping-edit churn the backlog sprint had.
4. §4 (chapter-relative time + reposition) — needs the `ChapterResolver.currentChapter()`
   extension first.
5. §5 (sleep-at-chapter-end) — do next; it reuses the same
   `ChapterResolver.currentChapter()` extension §4 adds, so building on top of §4 avoids
   adding that seam twice.
6. §7 (rotary/bezel) — touches `PlayerActivity.kt` alongside §3/§4/§5, so group it with
   whichever of those is still in progress rather than as a fully separate pass.
7. §6 (offline mode semantics) — fully independent of the Player-screen work above
   (touches `ApiViewModel`/`LoginActivity` instead); can be done anytime, including
   first, once the resolution question is answered.
8. §8 (byte-budget Smart Delete + free-space query) — do the `StorageUtils` free-space
   query before or alongside §2's management screen, since that screen displays it;
   the Smart Delete byte-budget logic itself is independent and can land anytime.

Each item ships with its test per the sections above, verified via CI (`build-apk.yml`)
before merging, per this project's established workflow — no fix here on code-reading
confidence alone where a test is feasible.
