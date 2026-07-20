package kaf.audiobookshelfwearos.app.tiles

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
import androidx.wear.protolayout.material3.avatarImage
import androidx.wear.protolayout.material3.button
import androidx.wear.protolayout.material3.materialScopeWithResources
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.tile
import com.google.common.util.concurrent.ListenableFuture
import kaf.audiobookshelfwearos.app.MainApp
import kaf.audiobookshelfwearos.app.activities.BookListActivity
import kaf.audiobookshelfwearos.app.activities.PlayerActivity
import kaf.audiobookshelfwearos.app.data.LibraryItem
import kaf.audiobookshelfwearos.app.utils.ContinueListeningSelector
import java.io.File
import java.nio.ByteBuffer

/**
 * Phase 3 of the "Continue Listening" tile: adds cover art, read cache-only from the same
 * context.cacheDir/<id>.jpg file ApiViewModel.saveBitmapToCache already populates when Book
 * List/Chapter List load a cover -- no live network fetch inside this Tile callback, and no
 * Coil involved (cover art in this app is a hand-rolled OkHttp + file-cache pipeline, not a
 * Coil-managed cache, despite what the original backlog note assumed).
 *
 * avatarImage(resource = ..., protoLayoutResourceId = ...) auto-registers the resource through
 * the ProtoLayoutScope threaded in via materialScopeWithResources -- confirmed from
 * androidx.wear.tiles.TileService's own source: when a scope has resources, the framework sends
 * them bundled with the tile data itself, so no onTileResourcesRequest override is needed here.
 *
 * See CLAUDE.md's Tiles backlog entry for the remaining phases (freshness triggering,
 * instrumented test).
 */
// open so ContinueListeningTileServiceTest's TestableContinueListeningTileService can
// subclass it (Kotlin classes are final by default; kapt's generated Java stub inherits
// that finality, which is what actually blocked the test -- see the test file's comment).
open class ContinueListeningTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = SuspendToFutureAdapter.launchFuture {
        val item = mostRecentContinueListeningItem()
        val coverResource = item?.let { coverImageResource(it.id) }
        val layout = materialScopeWithResources(
            this@ContinueListeningTileService,
            requestParams.scope,
            requestParams.deviceConfiguration
        ) {
            primaryLayout(
                mainSlot = {
                    button(
                        onClick = clickable(id = "open_app", action = launchActionFor(item)),
                        width = expand(),
                        height = expand(),
                        colors = filledTonalButtonColors(),
                        labelContent = { text(labelFor(item).layoutString) },
                        secondaryLabelContent = item?.author?.let { author ->
                            { text(author.layoutString) }
                        },
                        iconContent = coverResource?.let { resource ->
                            {
                                avatarImage(
                                    resource = resource,
                                    protoLayoutResourceId = COVER_RESOURCE_ID
                                )
                            }
                        }
                    )
                }
            )
        }
        tile(Timeline.fromLayoutElement(layout))
    }

    private suspend fun mostRecentContinueListeningItem(): LibraryItem? {
        val database = (applicationContext as MainApp).database
        val items = database.libraryItemDao().getAllLibraryItems()
        return ContinueListeningSelector.select(items).firstOrNull()
    }

    // internal (not private): ContinueListeningTileServiceTest asserts on these directly
    // rather than re-deriving them by walking the rendered LayoutElement tree.
    internal fun labelFor(item: LibraryItem?): String = item?.title ?: "Open ShelfTime"

    internal fun launchActionFor(item: LibraryItem?): ActionBuilders.Action {
        val activityBuilder = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(packageName)
        if (item != null) {
            activityBuilder
                .setClassName(PlayerActivity::class.java.name)
                .addKeyToExtraMapping("id", ActionBuilders.stringExtra(item.id))
        } else {
            activityBuilder.setClassName(BookListActivity::class.java.name)
        }
        return ActionBuilders.LaunchAction.Builder()
            .setAndroidActivity(activityBuilder.build())
            .build()
    }

    internal fun coverImageResource(itemId: String): ResourceBuilders.ImageResource? {
        val file = File(applicationContext.cacheDir, "$itemId.jpg")
        if (!file.exists()) return null
        val decoded = BitmapFactory.decodeFile(file.path) ?: return null
        val square = squareBitmap(decoded, COVER_TILE_SIZE_PX)
        val argb = if (square.config == Bitmap.Config.ARGB_8888) {
            square
        } else {
            square.copy(Bitmap.Config.ARGB_8888, false)
        }
        val buffer = ByteBuffer.allocate(argb.byteCount)
        argb.copyPixelsToBuffer(buffer)
        val inlineResource = ResourceBuilders.InlineImageResource.Builder()
            .setData(buffer.array())
            .setWidthPx(COVER_TILE_SIZE_PX)
            .setHeightPx(COVER_TILE_SIZE_PX)
            .setFormat(ResourceBuilders.IMAGE_FORMAT_ARGB_8888)
            .build()
        return ResourceBuilders.ImageResource.Builder()
            .setInlineResource(inlineResource)
            .build()
    }

    // Cover art is cached for Book List at full display size -- crop to a center square and
    // downscale, since the tile only needs a small avatar-sized icon.
    private fun squareBitmap(source: Bitmap, size: Int): Bitmap {
        val cropSize = minOf(source.width, source.height)
        val x = (source.width - cropSize) / 2
        val y = (source.height - cropSize) / 2
        val cropped = Bitmap.createBitmap(source, x, y, cropSize, cropSize)
        return Bitmap.createScaledBitmap(cropped, size, size, true)
    }

    companion object {
        private const val COVER_RESOURCE_ID = "cover_art"
        internal const val COVER_TILE_SIZE_PX = 64
    }
}
