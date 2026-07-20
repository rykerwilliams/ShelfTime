package kaf.audiobookshelfwearos.app.tiles

import android.content.ComponentName
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.TimelineBuilders.Timeline
import androidx.wear.protolayout.material3.ButtonDefaults.filledTonalButtonColors
import androidx.wear.protolayout.material3.button
import androidx.wear.protolayout.material3.materialScopeWithResources
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.modifiers.clickable
import androidx.wear.protolayout.modifiers.launchAction
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import androidx.wear.tiles.tile
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kaf.audiobookshelfwearos.app.activities.BookListActivity

/**
 * Phase 1 of the "Continue Listening" tile: proves the tile registers and
 * pins end-to-end (system binds it, layout renders, tap opens the app)
 * before wiring in real Continue Listening data, cover art, and progress --
 * see CLAUDE.md's Tiles backlog entry for the follow-up phases.
 *
 * BookListActivity had to be flipped to android:exported="true" for the tap
 * target to work -- Tile LaunchActions are fired by the system's tile-host
 * process, so the target activity must be externally launchable, same as
 * Android's own Tiles codelab requires for its sample MainActivity. Low risk
 * here specifically because BookListActivity doesn't read any Intent extras.
 */
class ContinueListeningTileService : TileService() {

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest
    ): ListenableFuture<TileBuilders.Tile> {
        val openAppClickable = clickable(
            id = "open_app",
            action = launchAction(ComponentName(this, BookListActivity::class.java))
        )
        val layout = materialScopeWithResources(
            this,
            requestParams.scope,
            requestParams.deviceConfiguration
        ) {
            primaryLayout(
                mainSlot = {
                    button(
                        onClick = openAppClickable,
                        width = expand(),
                        height = expand(),
                        colors = filledTonalButtonColors(),
                        labelContent = { text("Open ShelfTime".layoutString) }
                    )
                }
            )
        }
        return Futures.immediateFuture(tile(Timeline.fromLayoutElement(layout)))
    }
}
