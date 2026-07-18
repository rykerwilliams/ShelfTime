package kaf.audiobookshelfwearos.app.utils

/**
 * Decides whether ApiHandler.updateProgress() should do its pre-upload
 * getMediaProgress() GET + lastUpdate comparison before PATCHing progress.
 *
 * That pre-check exists to avoid clobbering newer progress the server already
 * has (e.g. progress recorded by another device since this one last synced).
 * Staleness is only plausible on paths where time may have passed since this
 * device last knew the server's state - sync-on-reconnect and the periodic
 * background SyncWorker pass. During an active playback session, the device
 * doing the periodic save *is* the newest-progress source by definition, so
 * the check there is a pure wasted GET on the app's highest-frequency network
 * call.
 */
object ProgressSyncPolicy {
    fun shouldCheckServerBeforeUpload(isPeriodicActiveSave: Boolean): Boolean {
        return !isPeriodicActiveSave
    }
}
