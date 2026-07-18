package kaf.audiobookshelfwearos.app.utils

/**
 * Single source of truth for the local broadcast action *suffixes* PlayerService
 * sends and PlayerActivity listens for (both still prefix with `$packageName.` at
 * the call site, since that requires a Context this pure object deliberately
 * doesn't depend on).
 *
 * Before this existed, PlayerService sent "$packageName.ACTION_PAUSE" while
 * PlayerActivity's IntentFilter/when-branch listened for
 * "$packageName.ACTION_PAUSED" - the strings never matched, so the pause
 * broadcast was silently dropped on every pause not driven by the on-screen
 * play/pause button (sleep timer, Bluetooth/crown media-button pause via the
 * MediaSession, etc). Routing both sides through these constants makes that
 * exact class of typo a compile error instead of a silent runtime no-op.
 */
object PlayerBroadcastActions {
    const val PLAYING = "ACTION_PLAYING"
    const val PAUSED = "ACTION_PAUSED"
    const val BUFFERING = "ACTION_BUFFERING"
    const val UPDATE_METADATA = "ACTION_UPDATE_METADATA"
}
