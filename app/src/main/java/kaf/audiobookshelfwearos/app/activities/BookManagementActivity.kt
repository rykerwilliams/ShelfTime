package kaf.audiobookshelfwearos.app.activities

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
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
import kaf.audiobookshelfwearos.app.utils.DownloadProgressCalculator
import kaf.audiobookshelfwearos.app.utils.StorageUtils
import kaf.audiobookshelfwearos.app.viewmodels.ApiViewModel
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
        var isDownloaded by remember {
            mutableStateOf(
                libraryItem.media.tracks.all { track -> track.isDownloaded(this) }
            )
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
                    if (isDownloaded) {
                        for (track in libraryItem.media.tracks) {
                            MyDownloadService.sendRemoveDownload(
                                this@BookManagementActivity,
                                track
                            )
                        }
                        isDownloaded = false
                    } else {
                        saveAudiobookToDB(libraryItem)
                        for (track in libraryItem.media.tracks) {
                            MyDownloadService.sendAddDownload(
                                this@BookManagementActivity,
                                track
                            )
                        }
                        isDownloaded = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (isDownloaded) Color(0xFF8B0000) else Color(0xFF086409)
                )
            ) {
                Text(text = if (isDownloaded) "Delete" else "Download")
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
