package kaf.audiobookshelfwearos.app.utils

import android.content.Context
import android.util.Log
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persists every Timber log line to a file in the app's external files dir, so a day
 * of normal watch use can be pulled afterward with `adb pull` instead of needing a
 * live `adb logcat` session running the whole time. Rotates once the current file
 * passes MAX_FILE_SIZE_BYTES, keeping at most MAX_ROTATED_FILES old files.
 */
class FileLoggingTree(context: Context) : Timber.Tree() {
    private val logDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "logs")
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    companion object {
        private const val MAX_FILE_SIZE_BYTES = 2L * 1024 * 1024
        private const val MAX_ROTATED_FILES = 5
        private const val CURRENT_LOG_NAME = "shelftime.log"
    }

    init {
        logDir.mkdirs()
    }

    @Synchronized
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        try {
            val logFile = File(logDir, CURRENT_LOG_NAME)
            if (logFile.exists() && logFile.length() > MAX_FILE_SIZE_BYTES) {
                rotate(logFile)
            }

            val level = when (priority) {
                Log.VERBOSE -> "V"
                Log.DEBUG -> "D"
                Log.INFO -> "I"
                Log.WARN -> "W"
                Log.ERROR -> "E"
                else -> "?"
            }

            FileOutputStream(logFile, true).use { fos ->
                fos.write("${dateFormat.format(Date())} $level/${tag ?: "ShelfTime"}: $message\n".toByteArray())
                t?.let { fos.write((Log.getStackTraceString(it) + "\n").toByteArray()) }
            }
        } catch (e: Exception) {
            // Logging must never be the thing that crashes the app.
        }
    }

    private fun rotate(logFile: File) {
        for (i in MAX_ROTATED_FILES - 1 downTo 1) {
            val src = File(logDir, "$CURRENT_LOG_NAME.$i")
            val dst = File(logDir, "$CURRENT_LOG_NAME.${i + 1}")
            if (src.exists()) src.renameTo(dst)
        }
        logFile.renameTo(File(logDir, "$CURRENT_LOG_NAME.1"))
    }
}
