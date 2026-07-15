package kaf.audiobookshelfwearos.app.services

import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import kaf.audiobookshelfwearos.app.data.Track

/**
 * Builds the ExoPlayer media source list for a track list. Deliberately takes no
 * Context/download-manager dependency, so it structurally cannot re-introduce a
 * per-track download-index query on the main thread before playback starts — the
 * bug this replaced. CacheDataSource already falls back from cache to network
 * transparently, so nothing here needs to know a track's download state.
 */
@OptIn(UnstableApi::class)
object PlayerMediaSourceBuilder {
    fun buildSources(
        tracks: List<Track>,
        baseUrl: String,
        artist: String,
        title: String,
        cacheDataSourceFactory: DataSource.Factory
    ): List<MediaSource> {
        return tracks.map { track ->
            val mediaItem = MediaItem.Builder()
                .setMediaId("track-index-" + track.index)
                .setUri(baseUrl + track.contentUrl)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setArtist(artist)
                        .setTitle(title)
                        .build()
                )
                .build()

            ProgressiveMediaSource.Factory(cacheDataSourceFactory)
                .createMediaSource(mediaItem)
        }
    }
}
