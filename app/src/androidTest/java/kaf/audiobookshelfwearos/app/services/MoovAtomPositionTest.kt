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
 * start playback) at the END of the file instead of the front. Over a real network
 * that forces the extractor to jump to the tail to fetch moov, then seek BACKWARD
 * to the start of `mdat` to actually begin reading playback samples - an extra,
 * discontiguous round trip that a faststart file never needs.
 *
 * The fixtures under androidTest/assets/mp4_fixtures are a synthetic silent track
 * (not the reporter's real book - this keeps things copyright-clean and tiny, ~76KB)
 * muxed twice: once with ffmpeg's -movflags +faststart, once without. `moov` in the
 * "moov_end" fixture is a real ~38KB box (from ~9000 AAC frames), large enough that
 * its position is observable in which byte ranges MockWebServer sees requested,
 * rather than needing the real gigabyte-scale file to demonstrate the mechanism.
 */
@RunWith(AndroidJUnit4::class)
class MoovAtomPositionTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun moovAtEndOfFileForcesABackwardSeekToReachAudioData() {
        val ranges = playFixtureAndRecordRequestedRanges("mp4_fixtures/moov_end.m4a")

        assertTrue(
            "Expected a backward seek (fetch moov near the file's tail, then seek " +
                "back to the start of mdat to begin playback) - this is the extra " +
                "round trip that causes slow playback start on a real network. " +
                "Requested range start offsets: ${ranges.map { it.start }}",
            hasBackwardSeek(ranges)
        )
    }

    @Test
    fun moovAtFrontOfFileNeverNeedsABackwardSeek() {
        val ranges = playFixtureAndRecordRequestedRanges("mp4_fixtures/moov_front.m4a")

        assertTrue(
            "Did not expect a backward seek - moov is already at the front, so " +
                "everything needed to reach READY should be one forward march " +
                "through the file. Requested range start offsets: ${ranges.map { it.start }}",
            !hasBackwardSeek(ranges)
        )
    }

    private data class RequestedRange(val start: Long)

    private fun hasBackwardSeek(ranges: List<RequestedRange>): Boolean {
        var maxSeenSoFar = -1L
        for (range in ranges) {
            if (range.start < maxSeenSoFar) return true
            maxSeenSoFar = maxOf(maxSeenSoFar, range.start)
        }
        return false
    }

    private fun playFixtureAndRecordRequestedRanges(assetPath: String): List<RequestedRange> {
        val bytes = InstrumentationRegistry.getInstrumentation().context.assets
            .open(assetPath).use { it.readBytes() }

        val recordedRanges = CopyOnWriteArrayList<RequestedRange>()

        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val rangeHeader = request.getHeader("Range")
                if (rangeHeader == null) {
                    recordedRanges.add(RequestedRange(0))
                    val fullBody = Buffer()
                    fullBody.write(bytes)
                    return MockResponse()
                        .setResponseCode(200)
                        .setHeader("Accept-Ranges", "bytes")
                        .setHeader("Content-Length", bytes.size.toString())
                        .setBody(fullBody)
                }

                val start = rangeHeader.removePrefix("bytes=").substringBefore("-").toLong()
                recordedRanges.add(RequestedRange(start))
                val slice = bytes.copyOfRange(start.toInt(), bytes.size)
                val partialBody = Buffer()
                partialBody.write(slice)
                return MockResponse()
                    .setResponseCode(206)
                    .setHeader("Accept-Ranges", "bytes")
                    .setHeader("Content-Range", "bytes $start-${bytes.size - 1}/${bytes.size}")
                    .setHeader("Content-Length", slice.size.toString())
                    .setBody(partialBody)
            }
        }
        server.start()

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

        return recordedRanges
    }
}
