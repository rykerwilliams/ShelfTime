package kaf.audiobookshelfwearos.app.utils

import android.content.Context
import android.os.BatteryManager
import timber.log.Timber

/**
 * Logs a battery/memory snapshot tagged with a named event, so pulled logs show
 * before/after deltas around the moments that actually drain a watch battery
 * (a playback session, a download session) instead of just a raw log stream.
 */
object PerformanceLogger {
    fun logSnapshot(context: Context, event: String) {
        val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val batteryPercent = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1

        val runtime = Runtime.getRuntime()
        val usedMemoryMb = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)

        Timber.tag("Perf").i("[$event] battery=$batteryPercent% usedMemory=${usedMemoryMb}MB")
    }
}
