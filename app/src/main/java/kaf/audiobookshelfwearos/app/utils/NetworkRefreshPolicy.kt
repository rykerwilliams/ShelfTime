package kaf.audiobookshelfwearos.app.utils

/**
 * Pure gating decision for `ApiViewModel.getLibraries()` (UI_CHANGES_PLAN.md section 6,
 * minimal-fix version): whether the network refresh (`loadLibraries()` -- which calls
 * `ApiHandler.getAllLibraries()`/`getLibraryItems()`) should be attempted at all.
 *
 * Today, checking "Offline Mode" (`onlyDownloaded`) only filters what comes back from a
 * network call that gets attempted regardless -- so it doesn't skip the wasted
 * attempt/wait it sounds like it should. This is the minimal fix: when `onlyDownloaded`
 * is true, skip the network attempt entirely rather than making it and filtering
 * afterward. The local-DB-first display path is unaffected either way.
 */
object NetworkRefreshPolicy {
    /**
     * @param onlyDownloaded the "Offline Mode" flag (`UserDataManager.offlineMode`), i.e.
     * whether the UI should only show downloaded items.
     * @return true if the network refresh should be attempted.
     */
    fun shouldAttemptNetworkRefresh(onlyDownloaded: Boolean): Boolean {
        return !onlyDownloaded
    }
}
