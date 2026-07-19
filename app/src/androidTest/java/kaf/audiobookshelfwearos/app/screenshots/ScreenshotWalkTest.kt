package kaf.audiobookshelfwearos.app.screenshots

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadProgress as Media3DownloadProgress
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.WritableDownloadIndex
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import kaf.audiobookshelfwearos.app.MainApp
import kaf.audiobookshelfwearos.app.activities.BookListActivity
import kaf.audiobookshelfwearos.app.activities.BookManagementActivity
import kaf.audiobookshelfwearos.app.activities.ChapterListActivity
import kaf.audiobookshelfwearos.app.activities.SettingsActivity
import kaf.audiobookshelfwearos.app.data.Chapter
import kaf.audiobookshelfwearos.app.data.LibraryItem
import kaf.audiobookshelfwearos.app.data.Media
import kaf.audiobookshelfwearos.app.data.Metadata
import kaf.audiobookshelfwearos.app.data.Track
import kaf.audiobookshelfwearos.app.data.UserMediaProgress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Not a correctness test -- a documentation-generation walk. Seeds a few
 * realistic-looking fake LibraryItems straight into Room (same pattern as
 * BookListActivityTapTest's seeding, so there's no dependency on a real
 * Audiobookshelf server), launches each key screen via ActivityScenario, and
 * captures a screenshot of each by drawing the activity's own decorView into
 * a Bitmap via Canvas, then publishing it via MediaStore (see
 * takeScreenshot()'s doc for why both of those replaced more obvious first
 * attempts).
 *
 * Also fakes "downloaded" and "downloading" state for two of the seeded
 * books by writing directly into Media3's download index (see
 * putFakeDownload()'s doc) -- no real network download needed -- and drives
 * the Book List's swipe-to-reveal gesture via UiAutomator (By/UiDevice/
 * Until) to capture what each of the three swipe states actually looks
 * like, not just the resting list.
 *
 * Deliberately NOT the Compose UI testing APIs (createEmptyComposeRule(),
 * onNodeWithText(), etc.), which is the one dependency that caused
 * BookListActivityTapTest's unexplained "Failed to instantiate test runner
 * class" failures across four straight CI pushes. UiAutomator is a
 * separate artifact with no ties to that dependency graph -- it drives the
 * accessibility tree Compose already exposes, the same way a real user's
 * TalkBack session would, so it carries none of that risk.
 *
 * Excluded from the regular per-push instrumented-test job (see
 * build-apk.yml's `notClass` filter) since this doesn't assert anything --
 * it's only meant to be run by generate-screenshots.yml's manual
 * workflow_dispatch trigger.
 */
@RunWith(AndroidJUnit4::class)
class ScreenshotWalkTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    private val seededIds = mutableListOf<String>()
    private val fakeDownloadTrackIds = mutableListOf<String>()
    private lateinit var continueListeningItemId: String
    private lateinit var downloadedItemId: String
    private lateinit var downloadingItemId: String

    private fun database() =
        (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MainApp).database

    private fun fakeItem(
        id: String,
        title: String,
        author: String,
        narrator: String?,
        currentTimeSeconds: Double,
        lastUpdate: Long
    ): LibraryItem {
        val chapters = listOf(
            Chapter(id = 0, start = 0.0, end = 900.0, title = "Chapter 1"),
            Chapter(id = 1, start = 900.0, end = 1800.0, title = "Chapter 2"),
            Chapter(id = 2, start = 1800.0, end = 2700.0, title = "Chapter 3")
        )
        val tracks = listOf(
            Track(index = 0, startOffset = 0.0, duration = 900.0, title = "Chapter 1", contentUrl = "$id-track-0"),
            Track(index = 1, startOffset = 900.0, duration = 900.0, title = "Chapter 2", contentUrl = "$id-track-1"),
            Track(index = 2, startOffset = 1800.0, duration = 900.0, title = "Chapter 3", contentUrl = "$id-track-2")
        )
        return LibraryItem(
            id = id,
            media = Media(
                tracks = tracks,
                chapters = chapters,
                metadata = Metadata(title = title, authorName = author, narratorName = narrator),
                duration = 2700.0,
                size = 180_000_000L,
                numChapters = chapters.size
            ),
            userMediaProgress = UserMediaProgress(
                id = id,
                libraryItemId = id,
                currentTime = currentTimeSeconds,
                duration = 2700.0,
                isFinished = false,
                lastUpdate = lastUpdate
            )
        )
    }

    // Writes straight into the same Media3 download database the app's own
    // MyDownloadService.getDownloadManager() reads from -- DownloadManager's
    // downloadIndex getter only exposes the read-only DownloadIndex
    // interface, but the underlying storage is a DefaultDownloadIndex over a
    // StandaloneDatabaseProvider(context), and constructing a fresh instance
    // of both with the same Context resolves to the exact same on-disk
    // database (that's the whole point of "Standalone"). No real download
    // ever runs; this just makes Track.isDownloaded()/isDownloading() (and
    // everything built on them) see the state we want for a screenshot.
    @OptIn(UnstableApi::class)
    private fun putFakeDownload(
        index: WritableDownloadIndex,
        trackId: String,
        state: Int,
        contentLength: Long,
        bytesDownloaded: Long,
        percentDownloaded: Float
    ) {
        val request = DownloadRequest.Builder(trackId, Uri.parse("https://fake.shelftime.example/$trackId")).build()
        val progress = Media3DownloadProgress().apply {
            this.bytesDownloaded = bytesDownloaded
            this.percentDownloaded = percentDownloaded
        }
        val download = Download(
            request,
            state,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            contentLength,
            Download.STOP_REASON_NONE,
            Download.FAILURE_REASON_NONE,
            progress
        )
        index.putDownload(download)
        fakeDownloadTrackIds.add(trackId)
    }

    @OptIn(UnstableApi::class)
    @Before
    fun seedFakeLibrary() = runBlocking {
        val items = listOf(
            fakeItem(
                id = "screenshot-continue-listening",
                title = "The Wayfinder's Log",
                author = "Mara Okonkwo",
                narrator = "Daniel Weiss",
                currentTimeSeconds = 1800.0,
                lastUpdate = System.currentTimeMillis()
            ),
            fakeItem(
                id = "screenshot-not-started",
                title = "Silent Orbit",
                author = "Rhea Vasquez",
                narrator = null,
                currentTimeSeconds = 0.0,
                lastUpdate = 0L
            ),
            fakeItem(
                id = "screenshot-downloaded",
                title = "Harbor Lights",
                author = "Julian Feld",
                narrator = "Ines Marchetti",
                currentTimeSeconds = 0.0,
                lastUpdate = 0L
            ),
            fakeItem(
                id = "screenshot-downloading",
                title = "Midnight Static",
                author = "Priya Anand",
                narrator = null,
                currentTimeSeconds = 0.0,
                lastUpdate = 0L
            )
        )
        for (item in items) {
            database().libraryItemDao().insertLibraryItem(item)
            seededIds.add(item.id)
        }
        continueListeningItemId = items[0].id
        downloadedItemId = items[2].id
        downloadingItemId = items[3].id

        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val downloadIndex = DefaultDownloadIndex(StandaloneDatabaseProvider(targetContext))
        val perTrackBytes = 60_000_000L

        // "Harbor Lights": all 3 tracks completed -- Track.isDownloaded()
        // requires every track in the book to be STATE_COMPLETED.
        for (i in 0..2) {
            putFakeDownload(
                downloadIndex,
                "screenshot-downloaded-track-$i",
                Download.STATE_COMPLETED,
                perTrackBytes,
                perTrackBytes,
                100f
            )
        }

        // "Midnight Static": one track done, one mid-flight, one queued --
        // isDownloading() only needs one DOWNLOADING track, and this also
        // gives AudiobookProgressCalculator a realistic ~50% to display.
        putFakeDownload(
            downloadIndex, "screenshot-downloading-track-0",
            Download.STATE_COMPLETED, perTrackBytes, perTrackBytes, 100f
        )
        putFakeDownload(
            downloadIndex, "screenshot-downloading-track-1",
            Download.STATE_DOWNLOADING, perTrackBytes, perTrackBytes / 2, 50f
        )
        putFakeDownload(
            downloadIndex, "screenshot-downloading-track-2",
            Download.STATE_QUEUED, perTrackBytes, 0L, 0f
        )
    }

    @OptIn(UnstableApi::class)
    @After
    fun removeFakeLibrary() = runBlocking {
        for (id in seededIds) {
            database().libraryItemDao().getLibraryItemById(id)?.let {
                database().libraryItemDao().deleteLibraryItem(it)
            }
        }
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val downloadIndex = DefaultDownloadIndex(StandaloneDatabaseProvider(targetContext))
        for (trackId in fakeDownloadTrackIds) {
            downloadIndex.removeDownload(trackId)
        }
    }

    // Two things had to be fixed to get a real screenshot out of this
    // headless CI emulator:
    // 1. UiAutomation.takeScreenshot() silently returned null on every call
    //    (it has a quiet `?: return`), almost certainly because this
    //    software-rendered emulator can't service a SurfaceFlinger
    //    frame-buffer readback (logcat showed repeated "Failed to find
    //    ColorBuffer" errors). Drawing the activity's own root View into a
    //    Bitmap via Canvas sidesteps the display pipeline entirely.
    // 2. Writing to getExternalFilesDir() (the app-private
    //    /Android/data/<pkg>/files tree) succeeded on-device, but that whole
    //    tree is invisible to `adb pull`/`find` under Android 11+ scoped
    //    storage, even with root adb -- and `run-as`, the normal escape
    //    hatch, reported "unknown package" on this particular Wear image.
    //    Inserting via MediaStore into a public Pictures/ collection instead
    //    isn't subject to that restriction, so plain `adb pull` works.
    private fun takeScreenshot(scenario: ActivityScenario<out Activity>, name: String) {
        // Give Compose a moment to finish rendering after the activity reaches
        // RESUMED -- there's no test-framework idling resource here (deliberately
        // avoiding the Compose testing APIs, see the class doc), so this is a
        // plain fixed delay rather than a poll-until-idle.
        Thread.sleep(2_000)
        var bitmap: Bitmap? = null
        scenario.onActivity { activity ->
            val view = activity.window.decorView
            val width = view.width
            val height = view.height
            if (width > 0 && height > 0) {
                bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also {
                    view.draw(Canvas(it))
                }
            } else {
                Log.e("ScreenshotWalkTest", "$name: decorView has zero size ($width x $height), skipping")
            }
        }
        val captured = bitmap ?: return
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ShelfTimeScreenshots")
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            Log.e("ScreenshotWalkTest", "$name: MediaStore insert failed")
            return
        }
        resolver.openOutputStream(uri)?.use { out ->
            captured.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        Log.i("ScreenshotWalkTest", "$name: wrote screenshot to $uri")
    }

    // Finds a Book List row by its (short, distinctive) title via the
    // accessibility tree Compose already populates, scrolling down a bounded
    // number of times if it isn't in the current viewport yet, then swipes
    // it open right-to-left (SwipeToRevealCard's reveal direction) and gives
    // the reveal animation a moment to settle.
    private fun swipeRowOpen(title: String): Boolean {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        var row = device.wait(Until.findObject(By.textContains(title)), 2_000)
        var attempts = 0
        while (row == null && attempts < 6) {
            val width = device.displayWidth
            val height = device.displayHeight
            device.swipe(width / 2, (height * 0.8).toInt(), width / 2, (height * 0.2).toInt(), 20)
            Thread.sleep(300)
            row = device.wait(Until.findObject(By.textContains(title)), 1_000)
            attempts++
        }
        if (row == null) {
            Log.e("ScreenshotWalkTest", "swipeRowOpen: couldn't find row for '$title' after scrolling")
            return false
        }
        // Three prior attempts (raw edge-to-edge at two speeds, then the
        // widest-ancestor's bounds, which turned out to span multiple rows
        // at once and caused an accidental scroll) all rendered identically
        // to the resting list. The likely real cause was staring us in the
        // face the whole time: docs/SCREENS.md's own description says "a
        // full swipe triggers the primary action immediately instead of
        // just revealing it" -- an edge-to-edge drag IS a full swipe, so
        // every attempt was likely auto-triggering (and then auto-closing)
        // the action instead of just revealing it. A partial drag across
        // ~45% of the screen width, anchored at the title text's own
        // (already-confirmed-correct) vertical center, should reveal
        // without crossing the full-swipe threshold.
        val bounds = row.visibleBounds
        val y = bounds.centerY()
        val screenWidth = device.displayWidth
        val startX = (screenWidth * 0.9).toInt()
        val endX = (screenWidth * 0.45).toInt()
        Log.i("ScreenshotWalkTest", "swipeRowOpen: '$title' textBounds=$bounds swipe=($startX,$y)->($endX,$y)")
        device.swipe(startX, y, endX, y, 60)
        Thread.sleep(800)
        return true
    }

    @Test
    fun captureKeyScreens() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

        ActivityScenario.launch(BookListActivity::class.java).use {
            takeScreenshot(it, "01_book_list")
        }

        ActivityScenario.launch(BookListActivity::class.java).use {
            if (swipeRowOpen("Silent Orbit")) {
                takeScreenshot(it, "01b_book_list_swipe_download")
            }
        }

        ActivityScenario.launch(BookListActivity::class.java).use {
            if (swipeRowOpen("Midnight Static")) {
                takeScreenshot(it, "01c_book_list_swipe_downloading")
            }
        }

        ActivityScenario.launch(BookListActivity::class.java).use {
            if (swipeRowOpen("Harbor Lights")) {
                takeScreenshot(it, "01d_book_list_swipe_delete")
            }
        }

        // The "id" extra has to be on the launch Intent itself, not set on the
        // Activity after the fact -- onCreate() (which reads it) has already run
        // by the time ActivityScenario.launch() returns.
        ActivityScenario.launch<ChapterListActivity>(
            Intent(targetContext, ChapterListActivity::class.java)
                .putExtra("id", continueListeningItemId)
        ).use {
            takeScreenshot(it, "02_chapter_list")
        }

        ActivityScenario.launch<ChapterListActivity>(
            Intent(targetContext, ChapterListActivity::class.java)
                .putExtra("id", downloadingItemId)
        ).use {
            takeScreenshot(it, "02b_chapter_list_downloading")
        }

        ActivityScenario.launch<BookManagementActivity>(
            Intent(targetContext, BookManagementActivity::class.java)
                .putExtra("id", continueListeningItemId)
        ).use {
            takeScreenshot(it, "03_book_management")
        }

        ActivityScenario.launch<BookManagementActivity>(
            Intent(targetContext, BookManagementActivity::class.java)
                .putExtra("id", downloadedItemId)
        ).use {
            takeScreenshot(it, "03b_book_management_downloaded")
        }

        ActivityScenario.launch(SettingsActivity::class.java).use {
            takeScreenshot(it, "04_settings")
        }
    }
}
