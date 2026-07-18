package kaf.audiobookshelfwearos.app.screenshots

import android.Manifest
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
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
import java.io.File
import java.io.FileOutputStream

/**
 * Not a correctness test -- a documentation-generation walk. Seeds a few
 * realistic-looking fake LibraryItems straight into Room (same pattern as
 * BookListActivityTapTest's seeding, so there's no dependency on a real
 * Audiobookshelf server), launches each key screen via ActivityScenario, and
 * captures a screenshot of each via UiAutomation.takeScreenshot() --
 * deliberately NOT the Compose UI testing APIs (createEmptyComposeRule(),
 * onNodeWithText(), etc.), which is the one dependency that caused
 * BookListActivityTapTest's unexplained "Failed to instantiate test runner
 * class" failures across four straight CI pushes. UiAutomation is part of
 * the core Android instrumentation framework (since API 18), not the
 * separate Compose testing artifact, so it carries none of that risk.
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
    private lateinit var continueListeningItemId: String

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
                id = "screenshot-not-started-1",
                title = "Silent Orbit",
                author = "Rhea Vasquez",
                narrator = null,
                currentTimeSeconds = 0.0,
                lastUpdate = 0L
            ),
            fakeItem(
                id = "screenshot-not-started-2",
                title = "The Cartographer's Dream",
                author = "Julian Feld",
                narrator = "Ines Marchetti",
                currentTimeSeconds = 0.0,
                lastUpdate = 0L
            )
        )
        for (item in items) {
            database().libraryItemDao().insertLibraryItem(item)
            seededIds.add(item.id)
        }
        continueListeningItemId = items.first().id
    }

    @After
    fun removeFakeLibrary() = runBlocking {
        for (id in seededIds) {
            database().libraryItemDao().getLibraryItemById(id)?.let {
                database().libraryItemDao().deleteLibraryItem(it)
            }
        }
    }

    private fun takeScreenshot(name: String) {
        // Give Compose a moment to finish rendering after the activity reaches
        // RESUMED -- there's no test-framework idling resource here (deliberately
        // avoiding the Compose testing APIs, see the class doc), so this is a
        // plain fixed delay rather than a poll-until-idle.
        Thread.sleep(2_000)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            ?: return
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(targetContext.getExternalFilesDir(null), "screenshots")
        dir.mkdirs()
        FileOutputStream(File(dir, "$name.png")).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }

    @Test
    fun captureKeyScreens() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext

        ActivityScenario.launch(BookListActivity::class.java).use {
            takeScreenshot("01_book_list")
        }

        // The "id" extra has to be on the launch Intent itself, not set on the
        // Activity after the fact -- onCreate() (which reads it) has already run
        // by the time ActivityScenario.launch() returns.
        ActivityScenario.launch<ChapterListActivity>(
            Intent(targetContext, ChapterListActivity::class.java)
                .putExtra("id", continueListeningItemId)
        ).use {
            takeScreenshot("02_chapter_list")
        }

        ActivityScenario.launch<BookManagementActivity>(
            Intent(targetContext, BookManagementActivity::class.java)
                .putExtra("id", continueListeningItemId)
        ).use {
            takeScreenshot("03_book_management")
        }

        ActivityScenario.launch(SettingsActivity::class.java).use {
            takeScreenshot("04_settings")
        }
    }
}
