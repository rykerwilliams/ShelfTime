package kaf.audiobookshelfwearos.app.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressSyncPolicyTest {

    @Test
    fun `periodic active-playback saves skip the pre-upload server check`() {
        assertFalse(ProgressSyncPolicy.shouldCheckServerBeforeUpload(isPeriodicActiveSave = true))
    }

    @Test
    fun `background and queued-sync paths keep the pre-upload server check`() {
        // Sync-on-reconnect (syncPendingProgress/syncAllPendingProgress), the periodic
        // SyncWorker pass, and the final on-pause save all call updateProgress() with
        // isPeriodicActiveSave=false (the default) - staleness is plausible there, so
        // the server's lastUpdate still needs to be checked before overwriting it.
        assertTrue(ProgressSyncPolicy.shouldCheckServerBeforeUpload(isPeriodicActiveSave = false))
    }
}
