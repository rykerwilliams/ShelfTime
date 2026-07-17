package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.UserMediaProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ProgressUploadBodyBuilderTest {

    @Test
    fun `built payload carries a non-zero duration when the source progress has one`() {
        val userMediaProgress = UserMediaProgress(
            id = "progress-1",
            libraryItemId = "item-1",
            duration = 3600.0,
            currentTime = 120.0,
            lastUpdate = 1_000_000L
        )

        val body = ProgressUploadBodyBuilder.buildBody(userMediaProgress)

        assertEquals(3600.0, body["duration"])
        assertNotEquals(0.0, body["duration"])
    }

    @Test
    fun `built payload always includes currentTime, lastUpdate, and duration`() {
        val userMediaProgress = UserMediaProgress(
            id = "progress-2",
            libraryItemId = "item-2",
            duration = 5400.0,
            currentTime = 42.0,
            lastUpdate = 2_000_000L
        )

        val body = ProgressUploadBodyBuilder.buildBody(userMediaProgress)

        assertEquals(42.0, body["currentTime"])
        assertEquals(2_000_000L, body["lastUpdate"])
        assertEquals(5400.0, body["duration"])
    }

    @Test
    fun `a zero duration source still round-trips as zero (not silently dropped)`() {
        val userMediaProgress = UserMediaProgress(
            id = "progress-3",
            libraryItemId = "item-3",
            duration = 0.0,
            currentTime = 10.0,
            lastUpdate = 3_000_000L
        )

        val body = ProgressUploadBodyBuilder.buildBody(userMediaProgress)

        // The builder itself is faithful to whatever duration it's given; it's
        // PlayerService's job to make sure a real duration is set before this is
        // called. This test just documents that the key is always present.
        assertEquals(true, body.containsKey("duration"))
        assertEquals(0.0, body["duration"])
    }
}
