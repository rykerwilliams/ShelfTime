package kaf.audiobookshelfwearos.app.utils

import android.content.Context
import java.io.File

// Free space must be queried against the same volume downloads actually land on -- on
// most devices getExternalFilesDir(null) and filesDir are the same volume, but some
// devices split internal/external storage onto different partitions, and
// MyDownloadService.getDownloadDirectory() already picks the one downloads use (with a
// filesDir fallback if external storage isn't available). Mirrored here rather than
// exposed from MyDownloadService's companion object, since that function is private
// and this needs no DownloadManager/Media3 dependency to answer "how much space is
// left".
object StorageUtils {

    fun getAvailableSpaceBytes(context: Context): Long {
        val downloadDirectory = context.getExternalFilesDir(null) ?: context.filesDir
        return downloadDirectory.freeSpace
    }

    fun getAvailableSpaceFormatted(context: Context): String {
        return DownloadProgressCalculator.formatBytes(getAvailableSpaceBytes(context))
    }
}
