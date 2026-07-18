package kaf.audiobookshelfwearos.app.utils

/**
 * Pure decision logic for whether starting one more download would exceed the
 * Smart Delete count/byte budget (UserDataManager.smartDeleteMaxDownloads /
 * smartDeleteMaxBytes). Deliberately a block, not an auto-eviction: unlike
 * SmartDeleteManager's after-the-fact cleanup (oldest-first, triggered once a
 * download completes), starting a brand new download while already over budget
 * is blocked outright -- the user clears space manually rather than the app
 * silently deciding what to delete on their behalf.
 */
object DownloadBudgetChecker {
    fun wouldExceedLimit(
        currentCount: Int,
        currentTotalBytes: Long,
        newItemBytes: Long,
        maxDownloads: Int,
        maxTotalBytes: Long
    ): Boolean {
        return (currentCount + 1) > maxDownloads || (currentTotalBytes + newItemBytes) > maxTotalBytes
    }
}
