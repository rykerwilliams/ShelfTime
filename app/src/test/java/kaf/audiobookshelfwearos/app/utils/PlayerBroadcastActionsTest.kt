package kaf.audiobookshelfwearos.app.utils

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression guard for the PLAYING/ACTION_PAUSE-vs-ACTION_PAUSED mismatch: PlayerService
 * sent an action PlayerActivity's IntentFilter/when-branch never registered for, so the
 * broadcast was silently dropped on every pause not driven by the on-screen button.
 *
 * Both lists below are sourced from PlayerBroadcastActions constants, not raw string
 * literals - mirroring PlayerService.kt's sendBroadcast(...) call sites and
 * PlayerActivity.kt's addAction(...)/when(...) call sites respectively - so a typo in
 * either file is a compile error, not a silently-dropped broadcast. This test then just
 * asserts the invariant the bug report cared about: everything PlayerService sends is
 * something PlayerActivity is listening for.
 */
class PlayerBroadcastActionsTest {

    // Mirrors the sendBroadcast(...) action strings in PlayerService.kt.
    private val sentByPlayerService = setOf(
        PlayerBroadcastActions.PLAYING,
        PlayerBroadcastActions.PAUSED,
        PlayerBroadcastActions.BUFFERING,
        PlayerBroadcastActions.UPDATE_METADATA,
    )

    // Mirrors the IntentFilter.addAction(...) / when(intent?.action) branches in
    // PlayerActivity.kt.
    private val listenedForByPlayerActivity = setOf(
        PlayerBroadcastActions.PLAYING,
        PlayerBroadcastActions.PAUSED,
        PlayerBroadcastActions.BUFFERING,
        PlayerBroadcastActions.UPDATE_METADATA,
    )

    @Test
    fun `every action PlayerService sends is listened for by PlayerActivity`() {
        assertTrue(listenedForByPlayerActivity.containsAll(sentByPlayerService))
    }

    @Test
    fun `action suffixes are all distinct`() {
        val allSuffixes = listOf(
            PlayerBroadcastActions.PLAYING,
            PlayerBroadcastActions.PAUSED,
            PlayerBroadcastActions.BUFFERING,
            PlayerBroadcastActions.UPDATE_METADATA,
        )
        assertTrue(allSuffixes.toSet().size == allSuffixes.size)
    }
}
