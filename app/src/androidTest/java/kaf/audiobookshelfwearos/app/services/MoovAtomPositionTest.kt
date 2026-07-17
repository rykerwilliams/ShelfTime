package kaf.audiobookshelfwearos.app.services

import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kaf.audiobookshelfwearos.app.data.Track
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Reproduces, at the container-format level, the real slow-playback-start report
 * against a large multi-hour audiobook: an MP4/M4B file muxed without "faststart"
 * stores its `moov` atom (the sample tables the MP4 extractor needs before it can
 * start playback) at the END of the file instead of the front.
 *
 * The metric that matters here isn't whether the extractor performs a discrete
 * "seek" (an earlier version of this test checked that, and learned the hard way
 * that it doesn't have to: a single open-ended Range request can stream straight
 * through mdat and into a trailing moov without ever issuing a second request).
 * What actually matters over a real, slow network is *how far into the file the
 * player had to read* before reaching READY - moov has to be fully received before
 * the extractor can resolve a single sample, so if moov sits at the tail, nearly
 * the whole file has to come down the wire first regardless of how many discrete
 * requests that took.
 *
 * The fixtures under androidTest/assets/mp4_fixtures are a synthetic silent track
 * (not the reporter's real book - this keeps things copyright-clean and tiny, ~76KB)
 * muxed twice: once with ffmpeg's -movflags +faststart, once without. `moov` in the
 * "moov_end" fixture is a real ~38KB box (from ~9000 AAC frames), large enough that
 * its position is observable in how far the requested/served byte ranges reach,
 * rather than needing the real gigabyte-scale file to demonstrate the mechanism.
 */
@RunWith(AndroidJUnit4::class)
class MoovAtomPositionTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun moovAtEndOfFileRequiresReadingNearlyTheWholeFileBeforeReady() {
        val moovEndFurthest = playFixtureAndGetFurthestByteReached("mp4_fixtures/moov_end.m4a")
        val moovFrontFurthest = playFixtureAndGetFurthestByteReached("mp4_fixtures/moov_front.m4a")

        assertTrue(
            "Expected reaching READY on the moov-at-end fixture to require reading " +
                "much further into the file than the moov-at-front fixture (moov " +
                "has to be fully received before the extractor can resolve a single " +
                "sample, so a trailing moov drags nearly the whole file along with " +
                "it). moov-at-end furthest byte: $moovEndFurthest, " +
                "moov-at-front furthest byte: $moovFrontFurthest",
            moovEndFurthest > moovFrontFurthest * 1.5
        )
    }

    private data class RequestedRange(val start: Long, val endInclusive: Long)

    private fun playFixtureAndGetFurthestByteReached(assetPath: String): Long {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open(assetPath).use { it.readBytes() }

        val recordedRanges = CopyOnWriteArrayList<RequestedRange>()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val rangeHeader = request.getHeader("Range")
                if (rangeHeader == null) {
                    recordedRanges.add(RequestedRange(0, (bytes.size - 1).toLong()))
                    val fullBody = Buffer()
                    fullBody.write(bytes)
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Length", bytes.size.toString())
                        .setBody(fullBody)
                }

                // "bytes=start-end" (bounded) or "bytes=start-" (open, meaning "to EOF").
                // Honoring the requested end matters: silently ignoring it and always
                // serving to EOF would hide whether the extractor actually asked for a
                // small bounded chunk versus a wide-open range.
                val spec = rangeHeader.removePrefix("bytes=")
                val start = spec.substringBefore("-").toLong()
                val requestedEnd = spec.substringAfter("-").takeIf { it.isNotEmpty() }?.toLong()
                val end = minOf(requestedEnd ?: (bytes.size - 1).toLong(), (bytes.size - 1).toLong())
                recordedRanges.add(RequestedRange(start, end))

                val slice = bytes.copyOfRange(start.toInt(), (end + 1).toInt())
                val partialBody = Buffer()
                partialBody.write(slice)
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Accept-Ranges", "bytes")
                    .setHeader("Content-Range", "bytes $start-$end/${bytes.size}")
                    .setHeader("Content-Length", slice.size.toString())
                    .setBody(partialBody)
            }
        }

        val track = Track(index = 0, contentUrl = "/track")
        val source = PlayerMediaSourceBuilder.buildSources(
            listOf(track),
            baseUrl = server.url("/").toString().trimEnd('/'),
            artist = null,
            title = null,
            cacheDataSourceFactory = DefaultHttpDataSource.Factory()
        ).single()

        val latch = CountDownLatch(1)
        var player: ExoPlayer? = null

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val exoPlayer = ExoPlayer.Builder(context).build()
            player = exoPlayer

            exoPlayer.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        latch.countDown()
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    latch.countDown()
                }
            })

            exoPlayer.setMediaSource(source)
            exoPlayer.prepare()
        }

        val reachedTerminalState = latch.await(15, TimeUnit.SECONDS)

        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            player?.release()
        }

        assertTrue("Player never reached READY (or errored) within timeout", reachedTerminalState)

        return recordedRanges.maxOf { it.endInclusive }
    }
}
