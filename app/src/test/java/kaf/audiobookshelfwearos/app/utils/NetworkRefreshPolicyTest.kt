package kaf.audiobookshelfwearos.app.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers UI_CHANGES_PLAN.md section 6's minimal fix: `ApiViewModel.getLibraries()`
 * should skip the network refresh entirely when "Offline Mode" (`onlyDownloaded`) is on,
 * rather than attempting it and filtering the result afterward.
 */
class NetworkRefreshPolicyTest {

    @Test
    fun `only downloaded means skip the network refresh`() {
        assertFalse(NetworkRefreshPolicy.shouldAttemptNetworkRefresh(onlyDownloaded = true))
    }

    @Test
    fun `not restricted to downloaded means attempt the network refresh`() {
        assertTrue(NetworkRefreshPolicy.shouldAttemptNetworkRefresh(onlyDownloaded = false))
    }
}
