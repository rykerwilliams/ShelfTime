package kaf.audiobookshelfwearos.app.activities

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.sp
import kaf.audiobookshelfwearos.app.data.AudiobookDownloadProgress
import kaf.audiobookshelfwearos.app.data.DownloadProgress
import kaf.audiobookshelfwearos.app.data.DownloadState
import kaf.audiobookshelfwearos.app.utils.AudiobookProgressCalculator
import kaf.audiobookshelfwearos.app.utils.DownloadProgressCalculator
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyColumnDefaults
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import kaf.audiobookshelfwearos.app.ApiHandler
import kaf.audiobookshelfwearos.app.MainApp
import kaf.audiobookshelfwearos.app.data.Chapter
import kaf.audiobookshelfwearos.app.data.LibraryItem
import kaf.audiobookshelfwearos.app.services.MyDownloadService
import kaf.audiobookshelfwearos.app.services.PlayerService
import kaf.audiobookshelfwearos.app.userdata.UserDataManager
import kaf.audiobookshelfwearos.app.viewmodels.ApiViewModel
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.floor

class ChapterListActivity : ComponentActivity() {
    var itemId: String = ""

    private val viewModel: ApiViewModel by viewModels {
        ApiViewModel.ApiViewModelFactory(
            ApiHandler(
                this
            )
        )
    }

    private var isOnlineMode: Boolean = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        isOnlineMode = !UserDataManager(this).offlineMode
        viewModel.setShowErrorTaosts(isOnlineMode)

        // Keep the screen on while this activity is in the foreground
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        viewModel.loginResult.observe(
            this
        ) { user ->
            Timber.d(user.token)
        }

        itemId = intent.getStringExtra("id") ?: ""

        Timber.i("itemId = $itemId")

        setContent {
            val libraryItem by viewModel.item.observeAsState()

            ManualLoadView(libraryItem, itemId)
            val scalingLazyListState = rememberScalingLazyListState(0)

            if (libraryItem?.id?.isNotEmpty() == true)
                libraryItem?.run {
                    Scaffold(
                        modifier = Modifier.onGloballyPositioned {},
                        positionIndicator = {
                            PositionIndicator(scalingLazyListState = scalingLazyListState)
                        },
                        vignette = {
                            Vignette(vignettePosition = VignettePosition.TopAndBottom)
                        }
                    ) {
                        ScalingLazyColumn(
                            state = scalingLazyListState,
                            scalingParams = ScalingLazyColumnDefaults.scalingParams(
                                edgeScale = 0.5f,
                                minTransitionArea = 0.5f,
                                maxTransitionArea = 0.5f
                            ),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(0.dp)
                        ) {
                            item {
                                AudiobookInfo(this@run)
                            }

                            item {
                                Text(
                                    textAlign = TextAlign.Center,
                                    text = "Chapters:", modifier = Modifier
                                        .padding(10.dp)
                                        .fillMaxWidth()
                                )
                            }

                            itemsIndexed(
                                media.chapters,
                                key = { _, chapter -> chapter.id }
                            ) { index, chapter ->
                                Chapter(this@run, chapter)
                                if (index != media.chapters.size) {
                                    Divider()
                                }
                            }

                        }
                    }
                }
        }
    }

    @Composable
    private fun AudiobookInfo(
        libraryItem: LibraryItem,
    ) {
        // Collect download progress from service
        val downloadProgressFlow = MyDownloadService.getProgressFlow()
        val trackProgresses = remember { mutableStateMapOf<String, DownloadProgress>() }
        var audiobookProgress by remember { mutableStateOf<AudiobookDownloadProgress?>(null) }

        var isDownloaded by remember {
            mutableStateOf(
                libraryItem.media.tracks.all { track -> track.isDownloaded(this) }
            )
        }

        var isDownloading by remember {
            mutableStateOf(
                libraryItem.media.tracks.any { track -> track.isDownloading(this) }
            )
        }

        var downloadedCount by remember {
            mutableStateOf(libraryItem.media.tracks.count { track -> track.isDownloaded(this) })
        }
        val totalTracks = libraryItem.media.tracks.size

        // Listen for progress updates
        LaunchedEffect(libraryItem.id) {
            try {
                Timber.d("Starting to collect download progress for audiobook: ${libraryItem.id}")
                downloadProgressFlow.collect { trackProgress ->
                    Timber.d("Received progress update for track: ${trackProgress.trackId}, progress: ${trackProgress.percentComplete}%")
                    
                    // Debug: Log all track IDs for this audiobook
                    val audiobookTrackIds = libraryItem.media.tracks.map { it.id }
                    Timber.d("Audiobook track IDs: $audiobookTrackIds")
                    
                    // Check if this progress update is for one of our tracks
                    if (libraryItem.media.tracks.any { it.id == trackProgress.trackId }) {
                        Timber.d("Progress update matches one of our tracks!")
                        trackProgresses[trackProgress.trackId] = trackProgress
                        
                        // Calculate overall audiobook progress
                        val currentProgresses = trackProgresses.values.toList()
                        if (currentProgresses.isNotEmpty()) {
                            audiobookProgress = AudiobookProgressCalculator.calculateAudiobookProgress(
                                libraryItem, 
                                currentProgresses
                            )
                            Timber.d("Updated audiobook progress: ${audiobookProgress?.overallProgress}%")
                        }
                        
                        // Update download states: one downloadIndex.getDownload() read per
                        // track (was up to three separate track.isDownloaded()/
                        // isDownloading() scans above, each issuing its own SQLite lookup
                        // for the same row), dispatched off the main thread since this is
                        // synchronous disk I/O running inside a Compose collect callback.
                        val statuses = withContext(Dispatchers.IO) {
                            libraryItem.media.tracks.map { track ->
                                MyDownloadService.getDownloadStatus(this@ChapterListActivity, track.id)
                            }
                        }
                        downloadedCount = statuses.count { it.isDownloaded }
                        isDownloading = statuses.any { it.isDownloading }
                        isDownloaded = statuses.all { it.isDownloaded }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Error collecting download progress")
            }
        }

        // MyDownloadService's listener only fires on state transitions (queued →
        // downloading → completed), never on byte-level progress, so the flow above
        // won't move the needle while a download is actually in flight — poll for that.
        LaunchedEffect(isDownloading) {
            while (isDownloading) {
                delay(2000L)
                try {
                    for (track in libraryItem.media.tracks) {
                        val progress = MyDownloadService.getDownloadProgress(this@ChapterListActivity, track.id)
                        if (progress != null && progress.state == DownloadState.DOWNLOADING) {
                            trackProgresses[track.id] = progress
                        }
                    }
                    if (trackProgresses.isNotEmpty()) {
                        audiobookProgress = AudiobookProgressCalculator.calculateAudiobookProgress(
                            libraryItem,
                            trackProgresses.values.toList()
                        )
                    }
                } catch (e: Exception) {
                    Timber.e(e, "Error polling download progress")
                }
            }
        }

        Column {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 15.dp, start = 30.dp, end = 30.dp)
                    .clickable {
                        val intent = Intent(this@ChapterListActivity, BookManagementActivity::class.java)
                        intent.putExtra("id", libraryItem.id)
                        startActivity(intent)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = libraryItem.title,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                if (isDownloaded) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        tint = Color.Gray,
                        imageVector = Icons.Filled.Done,
                        contentDescription = "Downloaded",
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            if (isDownloading) {
                val currentProgress = audiobookProgress
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 30.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val percent = currentProgress?.overallProgress ?: 0f
                    // Thin bar: orange track, green fill growing with progress --
                    // same convention as BookListActivity's swipe row and the book
                    // management screen's Download button, instead of a circular
                    // ring here.
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .background(Color(0xFFB8860B))
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (percent / 100f).coerceIn(0f, 1f))
                                .background(Color(0xFF086409))
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    if (currentProgress != null) {
                        Text(
                            text = if (percent < 0.1f) "<1%" else "${percent.toInt()}%",
                            fontSize = 8.sp,
                            color = Color.Gray
                        )
                        if (currentProgress.averageDownloadSpeed > 0) {
                            Text(
                                text = "${DownloadProgressCalculator.formatBytes(currentProgress.averageDownloadSpeed)}/s",
                                fontSize = 7.sp,
                                color = Color.Gray
                            )
                        }
                        if (currentProgress.estimatedTimeRemaining != Long.MAX_VALUE && currentProgress.estimatedTimeRemaining > 0) {
                            Text(
                                text = DownloadProgressCalculator.formatTime(currentProgress.estimatedTimeRemaining),
                                fontSize = 7.sp,
                                color = Color.Gray
                            )
                        }
                    } else {
                        // Fallback to simple track count display
                        Text(text = "Downloading...", fontSize = 8.sp, color = Color.Gray)
                        Text(text = "$downloadedCount / $totalTracks", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }

            PlayButton(libraryItem)
            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 1f),
                thickness = 1.dp
            )
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.getItem(this@ChapterListActivity, itemId)
    }

    @Composable
    fun PlayButton(item: LibraryItem) {
        // When this book is the one currently playing, there's nothing to
        // "Start"/"Continue" -- the chapter rows below are already the tap
        // target for seeking within it -- so render nothing here. A simple
        // one-line id comparison; not extracted into its own function since
        // there's no branching logic to unit test beyond the equality check
        // itself.
        if (item.id == PlayerService.currentlyPlayingItemId) {
            return
        }

        val buttonText = if (item.userProgress.currentTime > 10) "Continue" else "Start"

        Button(
            onClick = {
                saveAudiobookToDB(item)
                // Start the PlayerService
                PlayerService.setAudiobook(this, item, action = "continue")
                val intent = Intent(this@ChapterListActivity, PlayerActivity::class.java)
                startActivity(intent)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp),
            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFF086409))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(text = buttonText, fontSize = 16.sp)
            }
        }
    }

    private fun saveAudiobookToDB(item: LibraryItem) {
        lifecycleScope.launch {
            val db = (applicationContext as MainApp).database
            db.libraryItemDao().insertLibraryItem(item)
        }
    }

    @Preview(showBackground = true)
    @Composable
    fun AudiobookInfoPreview() {
        val libraryItem = LibraryItem()
        libraryItem.title = "Some Random Title"
        AudiobookInfo(libraryItem)
    }

    @Preview(showBackground = true)
    @Composable
    fun ChapterPreview() {
        Chapter(LibraryItem(), Chapter(0, 120.0, 260.0, "Chapter 1"))
    }

    @Composable
    private fun ManualLoadView(item: LibraryItem?, itemId: String) {
        val isLoading by viewModel.isLoading.collectAsState()

        if (isLoading) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center, modifier = Modifier
                    .fillMaxSize()
            ) {
                CircularProgressIndicator(
                    startAngle = 0f,
                    modifier = Modifier
                        .width(80.dp)
                        .height(80.dp),
                    indicatorColor = androidx.wear.compose.material.MaterialTheme.colors.secondary,
                    trackColor = androidx.wear.compose.material.MaterialTheme.colors.onBackground.copy(
                        alpha = 0.1f
                    ),
                    strokeWidth = 8.dp
                )
            }
        }

        if (item?.id!!.isEmpty() && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(0.dp)
            ) {

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center, modifier = Modifier
                        .fillMaxSize()
                ) {
                    Text(
                        text = "There was some problem. Try again.",
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(10.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(onClick = {
                        viewModel.getItem(this@ChapterListActivity, itemId)
                    }) {
                        Text(text = "LOAD")
                    }
                }
            }

        }
    }

    @Composable
    private fun Chapter(audiobook: LibraryItem, track: Chapter) {
        Column(modifier = Modifier
            .fillMaxWidth()
            .clickable {
                saveAudiobookToDB(audiobook)
                // Start the PlayerService
                PlayerService.setAudiobook(this, audiobook, track.start)
                val intent = Intent(this@ChapterListActivity, PlayerActivity::class.java)
                startActivity(intent)
            }
            .padding(16.dp)) {
            Text(
                text = track.title,
                textAlign = TextAlign.Center,
                fontSize = 12.sp,
                color = Color.LightGray,
                modifier = Modifier
                    .padding(start = 10.dp, end = 10.dp)
                    .fillMaxWidth()
            )
            Text(
                text = timeToString(track.start),
                fontSize = 10.sp,
                color = if (track.start > audiobook.userProgress.currentTime) Color.Green else
                    if (track.end > audiobook.userProgress.currentTime) Color.Cyan else Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(start = 10.dp, end = 10.dp)
                    .fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
    }

    private fun timeToString(seconds: Double): String {
        val totalSeconds = floor(seconds).toInt()
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val secs = totalSeconds % 60

        val timeString = if (hours > 0) {
            String.format("%02d:%02d:%02d", hours, minutes, secs)
        } else {
            String.format("%02d:%02d", minutes, secs)
        }
        return timeString
    }
}
