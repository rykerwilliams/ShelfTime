package kaf.audiobookshelfwearos.app.utils

/**
 * Pure decision logic behind PlayerActivity's physical-Back-button handling.
 *
 * The Home button's single/double-press behavior is reserved by Wear OS itself
 * (app-switcher on double press) and can't be intercepted by any app, so it's off
 * the table entirely. The Back button's single press has to keep working as normal
 * navigation everywhere else in the app -- so PlayerActivity doesn't act on a back
 * press immediately; it waits a short window to see if a second press arrives, and
 * only then decides whether this was a double-press (toggle play/pause) or a
 * single one (actually navigate back).
 */
object BackPressCoordinator {
    const val DEFAULT_WINDOW_MILLIS = 350L

    /**
     * @param lastPressMillis timestamp of the previous back press, or null if this
     * is the first press since the screen opened (or since the window last elapsed).
     * @param nowMillis timestamp of this back press.
     * @return true if [nowMillis] falls within [windowMillis] of [lastPressMillis] --
     * i.e. this is the second half of a double-press, so the caller should cancel
     * the pending single-press navigation and toggle play/pause instead.
     */
    fun isDoublePress(
        lastPressMillis: Long?,
        nowMillis: Long,
        windowMillis: Long = DEFAULT_WINDOW_MILLIS
    ): Boolean {
        return lastPressMillis != null && (nowMillis - lastPressMillis) in 0..windowMillis
    }
}
