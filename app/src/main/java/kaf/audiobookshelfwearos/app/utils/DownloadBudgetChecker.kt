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

    /**
     * Separate from [wouldExceedLimit]: this is the actual physical device storage,
     * not the user-configured Smart Delete budget, so it's checked unconditionally
     * (not gated behind smartDeleteEnabled) -- no setting can make the disk bigger.
     */
    fun hasInsufficientDeviceSpace(availableBytes: Long, newItemBytes: Long): Boolean {
        return newItemBytes > availableBytes
    }
}
