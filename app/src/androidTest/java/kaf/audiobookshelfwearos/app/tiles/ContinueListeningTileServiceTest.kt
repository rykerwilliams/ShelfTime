package kaf.audiobookshelfwearos.app.tiles

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import com.google.common.util.concurrent.ListenableFuture
import kaf.audiobookshelfwearos.app.MainApp
import kaf.audiobookshelfwearos.app.activities.BookListActivity
import kaf.audiobookshelfwearos.app.activities.PlayerActivity
import kaf.audiobookshelfwearos.app.data.LibraryItem
import kaf.audiobookshelfwearos.app.data.Media
import kaf.audiobookshelfwearos.app.data.Metadata
import kaf.audiobookshelfwearos.app.data.UserMediaProgress
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileOutputStream
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
//
// Not private: ScreenshotWalkTest reuses this same test double to render the tile for a
// screenshot, rather than duplicating it.
class TestableContinueListeningTileService : ContinueListeningTileService() {
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

    private fun targetContext() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun database() = (targetContext().applicationContext as MainApp).database

    private fun attachedService(): TestableContinueListeningTileService {
        val service = TestableContinueListeningTileService()
        service.attachContext(targetContext().applicationContext)
        return service
    }

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
        val service = attachedService()

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

    // Phase 1/2: empty-state fallback label, exercised directly rather than by re-walking a
    // rendered layout tree -- labelFor/launchActionFor/coverImageResource are internal
    // specifically so tests can assert on the decision logic itself.
    @Test
    fun labelForFallsBackToOpenShelfTimeWhenItemIsNull() {
        val service = attachedService()
        assertEquals("Open ShelfTime", service.labelFor(null))
    }

    @Test
    fun labelForUsesItemTitleWhenItemIsPresent() {
        val service = attachedService()
        val item = LibraryItem(id = "x", media = Media(metadata = Metadata(title = "Some Title")))
        assertEquals("Some Title", service.labelFor(item))
    }

    // Phase 1: nothing in progress falls back to opening the app, with no "id" extra to
    // resume anything.
    @Test
    fun launchActionTargetsBookListActivityWhenNothingInProgress() {
        val service = attachedService()
        val action = service.launchActionFor(null) as ActionBuilders.LaunchAction
        assertEquals(BookListActivity::class.java.name, action.androidActivity?.className)
        assertTrue(action.androidActivity?.keyToExtraMapping?.isEmpty() == true)
    }

    // Phase 2: an in-progress item resumes straight into PlayerActivity with the "id" extra
    // PlayerActivity.onCreate()/PlayerService.onStartCommand() expect.
    @Test
    fun launchActionTargetsPlayerActivityWithIdExtraWhenItemInProgress() {
        val service = attachedService()
        val item = LibraryItem(id = "resume-me", media = Media(metadata = Metadata(title = "T")))
        val action = service.launchActionFor(item) as ActionBuilders.LaunchAction
        assertEquals(PlayerActivity::class.java.name, action.androidActivity?.className)
        val idExtra = action.androidActivity
            ?.keyToExtraMapping
            ?.get("id") as? ActionBuilders.AndroidStringExtra
        assertEquals("resume-me", idExtra?.value)
    }

    // Phase 3: no cached cover file -> no icon, rather than fetching over the network.
    @Test
    fun coverImageResourceReturnsNullWhenNothingCached() {
        val service = attachedService()
        assertNull(service.coverImageResource("tile-test-no-such-cached-cover"))
    }

    // Phase 3: a cached cover file (same context.cacheDir/<id>.jpg ApiViewModel.saveBitmapToCache
    // writes) becomes a small square raw-ARGB_8888 InlineImageResource.
    @Test
    fun coverImageResourceReturnsInlineImageWhenCoverIsCached() {
        val service = attachedService()
        val cachedId = "tile-test-cached-cover"
        val cacheFile = File(targetContext().cacheDir, "$cachedId.jpg")
        val bitmap = Bitmap.createBitmap(120, 200, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        FileOutputStream(cacheFile).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }

        try {
            val resource = service.coverImageResource(cachedId)
            assertNotNull("Expected a cover ImageResource when a cache file exists", resource)
            val inline = resource?.inlineResource
            assertNotNull("Expected the ImageResource to wrap an InlineImageResource", inline)
            assertEquals(ContinueListeningTileService.COVER_TILE_SIZE_PX, inline?.widthPx)
            assertEquals(ContinueListeningTileService.COVER_TILE_SIZE_PX, inline?.heightPx)
            assertEquals(ResourceBuilders.IMAGE_FORMAT_ARGB_8888, inline?.format)
        } finally {
            cacheFile.delete()
        }
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
