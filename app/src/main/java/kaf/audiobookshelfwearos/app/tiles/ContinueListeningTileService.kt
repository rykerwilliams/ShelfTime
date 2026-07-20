package kaf.audiobookshelfwearos.app.tiles

import androidx.concurrent.futures.SuspendToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
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

/**
 * Phase 2 of the "Continue Listening" tile: shows the title/author of the most
 * recently-progressed book (same selection logic as Book List's "Continue
 * Listening" section, via ContinueListeningSelector) and resumes it directly
 * through PlayerActivity on tap. Falls back to Phase 1's "Open ShelfTime" ->
 * BookListActivity behavior when there's nothing in progress.
 *
 * See CLAUDE.md's Tiles backlog entry for the remaining phases (cover art,
 * freshness triggering, instrumented test).
 */
class ContinueListeningTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> = SuspendToFutureAdapter.launchFuture {
        val item = mostRecentContinueListeningItem()
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

    private fun labelFor(item: LibraryItem?): String = item?.title ?: "Open ShelfTime"

    private fun launchActionFor(item: LibraryItem?): ActionBuilders.Action {
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
}
