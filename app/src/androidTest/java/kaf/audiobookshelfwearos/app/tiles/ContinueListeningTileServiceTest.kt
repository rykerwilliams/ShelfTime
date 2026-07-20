package kaf.audiobookshelfwearos.app.tiles

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.common.util.concurrent.ListenableFuture
import kaf.audiobookshelfwearos.app.MainApp
import kaf.audiobookshelfwearos.app.data.LibraryItem
import kaf.audiobookshelfwearos.app.data.Media
import kaf.audiobookshelfwearos.app.data.Metadata
import kaf.audiobookshelfwearos.app.data.UserMediaProgress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

// Phase 5 of the Continue Listening Tile plan: asserts the built layout contains the
// expected title/author given seeded Room state, matching Phase 2's real behavior.
//
// TileService.onTileRequest is protected, so a test-only subclass widens it to public --
// the standard Kotlin/Java pattern for testing a protected member without reflection.
// No Robolectric involved (androidx.wear.tiles:tiles-testing's own TestTileClient is
// Robolectric-based under the hood, which this project has deliberately avoided so far --
// see CLAUDE.md's testing philosophy notes -- so this drives onTileRequest directly on a
// real device/emulator instead).
private class TestableContinueListeningTileService : ContinueListeningTileService() {
    fun attachContext(context: Context) {
        attachBaseContext(context)
    }

    public override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = super.onTileRequest(requestParams)
}

@RunWith(AndroidJUnit4::class)
class ContinueListeningTileServiceTest {

    private lateinit var itemId: String

    private fun database() =
        (InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as MainApp).database

    @Before
    fun seedInProgressItem() = runBlocking {
        val item = LibraryItem(
            id = "tile-test-continue-listening",
            media = Media(
                metadata = Metadata(title = "Tile Test Book", authorName = "Tile Test Author"),
                duration = 3600.0
            ),
            userMediaProgress = UserMediaProgress(
                id = "tile-test-continue-listening",
                libraryItemId = "tile-test-continue-listening",
                currentTime = 1800.0,
                duration = 3600.0,
                isFinished = false,
                lastUpdate = System.currentTimeMillis()
            )
        )
        database().libraryItemDao().insertLibraryItem(item)
        itemId = item.id
    }

    @After
    fun removeSeededItem() = runBlocking {
        // Not `?.let { }` -- that expression's type is Unit?, and JUnit rejects an
        // @After method whose return type isn't exactly Unit ("should be void").
        val item = database().libraryItemDao().getLibraryItemById(itemId)
        if (item != null) {
            database().libraryItemDao().deleteLibraryItem(item)
        }
    }

    @Test
    fun tileShowsMostRecentContinueListeningItemTitleAndAuthor() {
        val service = TestableContinueListeningTileService()
        service.attachContext(
            InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        )

        val deviceParameters = DeviceParametersBuilders.DeviceParameters.Builder()
            .setScreenWidthDp(192)
            .setScreenHeightDp(192)
            .setScreenDensity(2.0f)
            .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
            .setFontScale(1.0f)
            .build()
        val requestParams = RequestBuilders.TileRequest.Builder()
            .setDeviceConfiguration(deviceParameters)
            .build()

        val tile = service.onTileRequest(requestParams).get(10, TimeUnit.SECONDS)
        val texts = mutableListOf<String>()
        val root = tile.tileTimeline?.timelineEntries?.firstOrNull()?.layout?.root
        collectText(root, texts)

        assertTrue("Expected tile text to include the book title, got: $texts", texts.contains("Tile Test Book"))
        assertTrue("Expected tile text to include the author, got: $texts", texts.contains("Tile Test Author"))
    }

    private fun collectText(element: LayoutElementBuilders.LayoutElement?, into: MutableList<String>) {
        when (element) {
            is LayoutElementBuilders.Text -> element.text?.value?.let(into::add)
            is LayoutElementBuilders.Box -> element.contents.forEach { collectText(it, into) }
            is LayoutElementBuilders.Column -> element.contents.forEach { collectText(it, into) }
            is LayoutElementBuilders.Row -> element.contents.forEach { collectText(it, into) }
            else -> {}
        }
    }
}
