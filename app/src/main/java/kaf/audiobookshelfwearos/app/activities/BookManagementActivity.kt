package kaf.audiobookshelfwearos.app.activities

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.lifecycle.lifecycleScope
import kaf.audiobookshelfwearos.app.ApiHandler
import kaf.audiobookshelfwearos.app.MainApp
import kaf.audiobookshelfwearos.app.data.LibraryItem
import kaf.audiobookshelfwearos.app.services.MyDownloadService
import kaf.audiobookshelfwearos.app.theme.AudiobookshelfWearOSTheme
import kaf.audiobookshelfwearos.app.userdata.UserDataManager
import kaf.audiobookshelfwearos.app.utils.AudiobookProgressCalculator
import kaf.audiobookshelfwearos.app.utils.DownloadBudgetChecker
import kaf.audiobookshelfwearos.app.utils.DownloadProgressCalculator
import kaf.audiobookshelfwearos.app.utils.NetworkConnectivityManager
import kaf.audiobookshelfwearos.app.utils.StorageUtils
import kaf.audiobookshelfwearos.app.viewmodels.ApiViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

class BookManagementActivity : ComponentActivity() {
    var itemId: String = ""

    private val viewModel: ApiViewModel by viewModels {
        ApiViewModel.ApiViewModelFactory(
            ApiHandler(
                this
            )
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        itemId = intent.getStringExtra("id") ?: ""
        Timber.i("BookManagementActivity itemId = $itemId")

        setContent {
            AudiobookshelfWearOSTheme {
                val libraryItem by viewModel.item.observeAsState()
                val isLoading by viewModel.isLoading.collectAsState()
                val scalingLazyListState = rememberScalingLazyListState(0)

                if (isLoading) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (libraryItem?.id?.isNotEmpty() == true) {
                    libraryItem?.run {
                        Scaffold(
                            positionIndicator = {
                                PositionIndicator(scalingLazyListState = scalingLazyListState)
                            },
                            vignette = {
                                Vignette(vignettePosition = VignettePosition.TopAndBottom)
                            }
                        ) {
                            ScalingLazyColumn(
                                state = scalingLazyListState,
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(0.dp)
                            ) {
                                item {
                                    BookManagementContent(this@run)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.getItem(this@BookManagementActivity, itemId)
    }

    @Composable
    private fun BookManagementContent(libraryItem: LibraryItem) {
        // Keyed on libraryItem.id (was a bare `remember` before, computed once and
        // never updated) -- this screen's Download button used to get stuck showing
        // whatever state it had on first composition, e.g. after onResume()'s
        // re-fetch or after a download that finished while this screen was open.
        var isDownloaded by remember(libraryItem.id) {
            mutableStateOf(
                libraryItem.media.tracks.all { track -> track.isDownloaded(this) }
            )
        }
        var isDownloading by remember(libraryItem.id) {
            mutableStateOf(libraryItem.media.tracks.any { track -> track.isDownloading(this) })
        }
        var downloadProgressPercent by remember(libraryItem.id) { mutableFloatStateOf(0f) }
        var downloadSpeedBytesPerSecond by remember(libraryItem.id) { mutableLongStateOf(0L) }
        var downloadedBytes by remember(libraryItem.id) { mutableLongStateOf(0L) }
        var downloadTotalBytes by remember(libraryItem.id) { mutableLongStateOf(0L) }

        // Reactively updates isDownloading/isDownloaded off MyDownloadService's
        // single shared DownloadManager.Listener-backed flow -- same mechanism
        // BookListActivity's swipe row and ChapterListActivity already use.
        LaunchedEffect(libraryItem.id) {
            MyDownloadService.getProgressFlow().collect { progress ->
                val trackIds = libraryItem.media.tracks.map { it.id }
                if (progress.trackId in trackIds) {
                    val statuses = trackIds.map { MyDownloadService.getDownloadStatus(this@BookManagementActivity, it) }
                    isDownloading = statuses.any { it.isDownloading }
                    isDownloaded = statuses.isNotEmpty() && statuses.all { it.isDownloaded }
                }
            }
        }

        // Continuous byte-level progress/speed never comes through the flow above
        // (Media3's DownloadManager.Listener only fires on state transitions) --
        // same poll-while-downloading pattern as BookListActivity/ChapterListActivity,
        // bounded to only run while this screen's book is actively downloading.
        LaunchedEffect(isDownloading) {
            while (isDownloading) {
                val progresses = libraryItem.media.tracks.mapNotNull {
                    MyDownloadService.getDownloadProgress(this@BookManagementActivity, it.id)
                }
                if (progresses.isNotEmpty()) {
                    val aggregate = AudiobookProgressCalculator.calculateAudiobookProgress(libraryItem, progresses)
                    downloadProgressPercent = aggregate.overallProgress
                    downloadSpeedBytesPerSecond = aggregate.averageDownloadSpeed
                    downloadedBytes = aggregate.totalBytesDownloaded
                    downloadTotalBytes = aggregate.totalBytes
                }
                delay(2000L)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = libraryItem.title,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 12.dp)
            )

            InfoRow("Size", DownloadProgressCalculator.formatBytes(libraryItem.media.size))
            InfoRow("Chapters", "${libraryItem.media.chapters.size}")
            InfoRow("Author", libraryItem.author)

            val narratorName = libraryItem.media.metadata.narratorName
            if (!narratorName.isNullOrEmpty()) {
                InfoRow("Narrator", narratorName)
            }

            InfoRow("Free Space", StorageUtils.getAvailableSpaceFormatted(this@BookManagementActivity))

            Button(
                onClick = {
                    if (isDownloaded || isDownloading) {
                        // Media3's DownloadManager.removeDownload() cancels an
                        // in-flight download and deletes a completed one via the
                        // same call, so Cancel and Delete share this one branch.
                        for (track in libraryItem.media.tracks) {
                            MyDownloadService.sendRemoveDownload(
                                this@BookManagementActivity,
                                track
                            )
                        }
                        isDownloaded = false
                        isDownloading = false
                    } else {
                        lifecycleScope.launch {
                            // sendAddDownload() sets no Requirements on its
                            // DownloadRequest, so Media3 defaults to requiring network
                            // and just parks a download in STATE_QUEUED with no
                            // user-visible signal that it's stalled -- check up front
                            // instead of letting that happen silently.
                            if (!NetworkConnectivityManager(this@BookManagementActivity) {}.isNetworkAvailable()) {
                                Toast.makeText(
                                    this@BookManagementActivity,
                                    "No internet connection -- connect and try again",
                                    Toast.LENGTH_LONG
                                ).show()
                                return@launch
                            }
                            // Checked unconditionally -- unlike the Smart Delete
                            // budget below, no setting can make the physical disk
                            // bigger.
                            val insufficientDeviceSpace = DownloadBudgetChecker.hasInsufficientDeviceSpace(
                                availableBytes = StorageUtils.getAvailableSpaceBytes(this@BookManagementActivity),
                                newItemBytes = libraryItem.media.size
                            )
                            val userDataManager = UserDataManager(this@BookManagementActivity)
                            val wouldExceedLimit = !insufficientDeviceSpace && userDataManager.smartDeleteEnabled && run {
                                val db = (applicationContext as MainApp).database
                                val downloadedItems = db.libraryItemDao().getAllLibraryItems()
                                    .filter { it.isDownloaded(this@BookManagementActivity) }
                                DownloadBudgetChecker.wouldExceedLimit(
                                    currentCount = downloadedItems.size,
                                    currentTotalBytes = downloadedItems.sumOf { it.media.size },
                                    newItemBytes = libraryItem.media.size,
                                    maxDownloads = userDataManager.smartDeleteMaxDownloads,
                                    maxTotalBytes = userDataManager.smartDeleteMaxBytes
                                )
                            }
                            if (insufficientDeviceSpace) {
                                // Same manual-deletion-required policy as the budget
                                // block below -- delete something, then try again.
                                Toast.makeText(
                                    this@BookManagementActivity,
                                    "Not enough storage space -- delete something first",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else if (wouldExceedLimit) {
                                // Deliberately blocks rather than auto-evicting like
                                // SmartDeleteManager's after-a-download cleanup does --
                                // starting a brand new download while already over
                                // budget means the user clears space manually rather
                                // than the app silently choosing what to delete.
                                Toast.makeText(
                                    this@BookManagementActivity,
                                    "Download would exceed your storage limit -- delete something first",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                saveAudiobookToDB(libraryItem)
                                for (track in libraryItem.media.tracks) {
                                    MyDownloadService.sendAddDownload(
                                        this@BookManagementActivity,
                                        track
                                    )
                                }
                                isDownloading = true
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = when {
                        isDownloaded -> Color(0xFF8B0000)
                        isDownloading -> Color(0xFFB8860B)
                        else -> Color(0xFF086409)
                    }
                )
            ) {
                if (isDownloading) {
                    // The button's own background (from `colors` above) is already
                    // amber -- fill the entire button with green from the left,
                    // growing with progress, same convention as BookListActivity's
                    // swipe row.
                    Box(modifier = Modifier.fillMaxSize()) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .fillMaxWidth(fraction = (downloadProgressPercent / 100f).coerceIn(0f, 1f))
                                .background(Color(0xFF086409))
                        )
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.align(Alignment.Center)
                        ) {
                            Text(text = "Cancel", fontSize = 14.sp, color = Color.White)
                            if (downloadTotalBytes > 0) {
                                Text(
                                    text = "${DownloadProgressCalculator.formatBytes(downloadedBytes)} / " +
                                        DownloadProgressCalculator.formatBytes(downloadTotalBytes),
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                            if (downloadSpeedBytesPerSecond > 0) {
                                Text(
                                    text = "${DownloadProgressCalculator.formatBytes(downloadSpeedBytesPerSecond)}/s",
                                    fontSize = 11.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                } else {
                    Text(text = if (isDownloaded) "Delete" else "Download")
                }
            }
        }
    }

    private fun saveAudiobookToDB(item: LibraryItem) {
        lifecycleScope.launch {
            val db = (applicationContext as MainApp).database
            db.libraryItemDao().insertLibraryItem(item)
        }
    }

    @Composable
    private fun InfoRow(label: String, value: String) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            Text(
                text = label,
                fontSize = 10.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center
            )
            Text(
                text = value,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
