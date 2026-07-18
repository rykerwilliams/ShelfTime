package kaf.audiobookshelfwearos.app.utils

import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import android.content.Context
import android.widget.Toast
import kaf.audiobookshelfwearos.app.MainApp
import kaf.audiobookshelfwearos.app.services.MyDownloadService
import kaf.audiobookshelfwearos.app.services.PlayerService
import kaf.audiobookshelfwearos.app.userdata.UserDataManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import timber.log.Timber

class SmartDeleteManager(context: Context) {
    // Normalize to the application context immediately: this instance is often
    // built from whatever Context MyDownloadService's download-completion
    // listener happens to be holding, which can be a long-destroyed Activity.
    private val context = context.applicationContext
    private val userDataManager = UserDataManager(this.context)
    private val database = (this.context as MainApp).database

    fun triggerSmartDeleteAfterDownload() {
        if (!userDataManager.smartDeleteEnabled) return
        debouncer.trigger { performSmartDelete() }
    }

    @OptIn(UnstableApi::class)
    private suspend fun performSmartDelete() {
        try {
            val downloadManager = MyDownloadService.getDownloadManager(context)
            val downloadedItems = database.libraryItemDao().getAllLibraryItems()
                .filter { it.isDownloaded(context) }

            // Get download completion order by checking download index
            val itemsWithDownloadInfo = downloadedItems.mapNotNull { item ->
                val firstTrack = item.media.tracks.firstOrNull()
                if (firstTrack != null) {
                    val download = downloadManager.downloadIndex.getDownload(firstTrack.id)
                    if (download != null) {
                        item to download.updateTimeMs
                    } else null
                } else null
            }

            val itemsToDelete = SmartDeleteSelector.selectItemsToDelete(
                itemsWithDownloadInfo,
                PlayerService.currentlyPlayingItemId,
                userDataManager.smartDeleteMaxDownloads,
                userDataManager.smartDeleteMaxBytes
            )
            Timber.d("Smart delete: ${itemsToDelete.size} item(s) to remove")

            for (item in itemsToDelete) {
                // Remove downloads using the existing service
                for (track in item.media.tracks) {
                    MyDownloadService.sendRemoveDownload(context, track)
                }

                // Remove from database
                database.libraryItemDao().deleteLibraryItem(item)

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Removed ${item.title} to make space",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                Timber.d("Smart delete removed: ${item.title}")
            }
        } catch (e: Exception) {
            Timber.e(e, "Error during smart delete")
        }
    }

    companion object {
        private val job = SupervisorJob()
        private val scope = CoroutineScope(Dispatchers.IO + job)
        private val debouncer = Debouncer(scope, delayMs = 3_000L)
    }
}
