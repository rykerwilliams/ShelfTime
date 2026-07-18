package kaf.audiobookshelfwearos.app.activities

import android.Manifest
import android.os.Build
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import kaf.audiobookshelfwearos.app.MainApp
import kaf.audiobookshelfwearos.app.data.LibraryItem
import kaf.audiobookshelfwearos.app.data.Media
import kaf.audiobookshelfwearos.app.data.Metadata
import kaf.audiobookshelfwearos.app.data.UserMediaProgress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression test for the "tracks must not be empty" crash reported live via
 * `adb logcat -b crash` (twice, same exception, different PIDs): BookListActivity's
 * tap-to-play routing (BookTapRouter.shouldJumpStraightToPlayback) used to hand a
 * summary-endpoint LibraryItem -- empty `media.tracks`, since getLibraries()'s
 * local-DB read reflects whatever was last saved, and a never-opened book has
 * never gone through ChapterListActivity's expanded fetch -- straight to
 * PlayerService.setAudiobook() -> TrackPositionResolver.resolve(), which
 * `require()`s a non-empty tracks list.
 *
 * This seeds exactly that shape of item into the local Room DB (the same table
 * getLibraries() reads from first, before any network round trip), taps its row
 * with nothing else playing (so BookTapRouter routes straight to playback instead
 * of the chapter list), and asserts the activity survives. The fix
 * (BookListActivity.resolveItemWithTracks()) fetches the expanded item first;
 * that network call fails in the emulator (no server configured) and
 * ApiHandler.getItem() catches that and returns null, so what's actually
 * exercised here is the fallback path (open ChapterListActivity instead) -- the
 * crash this guards against is the `require()` throwing before that fallback
 * logic ever gets a chance to run.
 */
@RunWith(AndroidJUnit4::class)
class BookListActivityTapTest {

    @get:Rule
    val permissionRule: GrantPermissionRule = if (Build.VERSION.SDK_INT >= 33) {
        GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        GrantPermissionRule.grant()
    }

    @get:Rule
    val composeTestRule = createEmptyComposeRule()

    private val seededItemId = "test-empty-tracks-${System.nanoTime()}"
    private val seededTitle = "Empty Tracks Regression Book"

    private fun database() =
        (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MainApp).database

    @Before
    fun seedLibraryItemWithNoLocalTrackData() = runBlocking {
        // currentlyPlayingItemId defaults to null and has a private setter (only
        // PlayerService itself may change it); nothing else in this suite starts
        // playback, so it's still null here without needing to touch it.
        database().libraryItemDao().insertLibraryItem(
            LibraryItem(
                id = seededItemId,
                media = Media(tracks = emptyList(), metadata = Metadata(title = seededTitle)),
                userMediaProgress = UserMediaProgress(id = seededItemId, libraryItemId = seededItemId)
            )
        )
    }

    @After
    fun removeSeededItem() = runBlocking {
        database().libraryItemDao().getLibraryItemById(seededItemId)?.let {
            database().libraryItemDao().deleteLibraryItem(it)
        }
    }

    @Test
    fun tappingBookWithNoLocalTrackData_doesNotCrash() {
        ActivityScenario.launch(BookListActivity::class.java).use { scenario ->
            composeTestRule.waitUntil(timeoutMillis = 15_000) {
                composeTestRule.onAllNodesWithText(seededTitle).fetchSemanticsNodes().isNotEmpty()
            }

            composeTestRule.onNodeWithText(seededTitle).performClick()

            // resolveItemWithTracks()'s network fetch times out at 3s in debug
            // builds (BuildConfig.DEBUG); give it room to fail and fall back
            // before asserting the activity is still alive.
            Thread.sleep(6_000)

            assertNotEquals(Lifecycle.State.DESTROYED, scenario.state)
        }
    }
}
