package kaf.audiobookshelfwearos.app.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Runs [action] after [delayMs], cancelling any not-yet-fired pending call
 * whenever [trigger] is called again. Used to collapse a burst of rapid
 * triggers (e.g. every track of a multi-track download completing within
 * milliseconds of each other) into a single execution.
 */
class Debouncer(private val scope: CoroutineScope, private val delayMs: Long) {
    private var pendingJob: Job? = null

    @Synchronized
    fun trigger(action: suspend () -> Unit) {
        pendingJob?.cancel()
        pendingJob = scope.launch {
            delay(delayMs)
            action()
        }
    }
}
