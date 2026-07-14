package kaf.audiobookshelfwearos.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TrackTest {

    @Test
    fun `id is derived from contentUrl, matching how downloads are keyed`() {
        val track = Track(contentUrl = "/api/items/abc/file/xyz")
        assertEquals("/api/items/abc/file/xyz", track.id)
    }
}
