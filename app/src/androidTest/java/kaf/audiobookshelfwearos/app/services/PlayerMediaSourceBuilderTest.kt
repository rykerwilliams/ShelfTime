package kaf.audiobookshelfwearos.app.services

import androidx.media3.datasource.DefaultHttpDataSource
import androidx.test.ext.junit.runners.AndroidJUnit4
import kaf.audiobookshelfwearos.app.data.Track
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

// Instrumented (not a plain unit test) because building a real ProgressiveMediaSource
// touches android.net.Uri, which isn't available on the unit-test classpath.
@RunWith(AndroidJUnit4::class)
class PlayerMediaSourceBuilderTest {

    // A plain DefaultHttpDataSource.Factory suffices here — the point of this test is
    // that buildSources() takes no Context/download-manager dependency at all, so it
    // has no way to touch the download index while resolving tracks.
    private val cacheDataSourceFactory = DefaultHttpDataSource.Factory()

    @Test
    fun buildsOneMediaSourcePerTrack() {
        val tracks = listOf(
            Track(index = 0, startOffset = 0.0, duration = 100.0, contentUrl = "/api/items/1/file/1"),
            Track(index = 1, startOffset = 100.0, duration = 120.0, contentUrl = "/api/items/1/file/2"),
            Track(index = 2, startOffset = 220.0, duration = 90.0, contentUrl = "/api/items/1/file/3"),
        )

        val sources = PlayerMediaSourceBuilder.buildSources(
            tracks,
            baseUrl = "http://example.com",
            artist = "Some Author",
            title = "Some Book",
            cacheDataSourceFactory = cacheDataSourceFactory
        )

        assertEquals(tracks.size, sources.size)
    }

    @Test
    fun mediaItemIdMatchesTrackIndex() {
        val tracks = listOf(
            Track(index = 5, startOffset = 0.0, duration = 60.0, contentUrl = "/api/items/1/file/5"),
        )

        val sources = PlayerMediaSourceBuilder.buildSources(
            tracks,
            baseUrl = "http://example.com",
            artist = "Some Author",
            title = "Some Book",
            cacheDataSourceFactory = cacheDataSourceFactory
        )

        assertEquals("track-index-5", sources[0].mediaItem.mediaId)
    }

    @Test
    fun mediaItemUriCombinesBaseUrlAndContentUrl() {
        val tracks = listOf(
            Track(index = 0, startOffset = 0.0, duration = 60.0, contentUrl = "/api/items/abc/file/0"),
        )

        val sources = PlayerMediaSourceBuilder.buildSources(
            tracks,
            baseUrl = "http://192.168.1.50:13378",
            artist = "Some Author",
            title = "Some Book",
            cacheDataSourceFactory = cacheDataSourceFactory
        )

        assertEquals(
            "http://192.168.1.50:13378/api/items/abc/file/0",
            sources[0].mediaItem.localConfiguration?.uri.toString()
        )
    }

    @Test
    fun emptyTrackListProducesNoSources() {
        val sources = PlayerMediaSourceBuilder.buildSources(
            emptyList(),
            baseUrl = "http://example.com",
            artist = "Some Author",
            title = "Some Book",
            cacheDataSourceFactory = cacheDataSourceFactory
        )

        assertEquals(0, sources.size)
    }
}
