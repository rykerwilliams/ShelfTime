package kaf.audiobookshelfwearos.app

import android.app.Application
import androidx.room.Room
import kaf.audiobookshelfwearos.BuildConfig
import kaf.audiobookshelfwearos.app.data.room.AppDatabase
import kaf.audiobookshelfwearos.app.utils.FileLoggingTree
import kaf.audiobookshelfwearos.app.utils.PerformanceLogger
import kaf.audiobookshelfwearos.app.workers.SyncWorker
import timber.log.Timber

class MainApp : Application() {
    lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(LineNumberDebugTree())
            Timber.plant(FileLoggingTree(this))

            // Crashlytics can't be relied on here — sideloaded builds ship with a
            // dummy google-services.json when no real one is configured, so crash
            // reports have nowhere real to go. Capture the crash to our own log file
            // before the process dies, then hand off to whatever handler was already
            // registered (Crashlytics' own, if present) so nothing else changes.
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                Timber.e(throwable, "FATAL: uncaught exception on thread ${thread.name}")
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
        PerformanceLogger.logSnapshot(this, "app_start")

        database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "library-item-db"
        ).fallbackToDestructiveMigration().build()
        
        // Initialize background sync
        SyncWorker.schedulePeriodicSync(this)
    }

    internal class LineNumberDebugTree : Timber.DebugTree()
    {
        override fun createStackElementTag(element: StackTraceElement): String {
            return "(${element.fileName}:${element.lineNumber})#${element.methodName}"
        }
    }
}