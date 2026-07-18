package kaf.audiobookshelfwearos.app.utils

import kaf.audiobookshelfwearos.app.data.UserMediaProgress

/**
 * Pure builder for the JSON body sent by the progress PATCH
 * (`/api/me/progress/:id`). Extracted out of ApiHandler.uploadProgress() so the
 * payload shape can be unit-tested without an Android/OkHttp/org.json dependency.
 *
 * `duration` MUST be included: it's what lets the Audiobookshelf server's
 * auto-finish heuristic work. Omitting it leaves the server-side MediaProgress
 * row's duration at its default of 0 forever, since no PATCH ever corrects it.
 */
object ProgressUploadBodyBuilder {
    fun buildBody(userMediaProgress: UserMediaProgress): Map<String, Any> {
        return mapOf(
            "currentTime" to userMediaProgress.currentTime,
            "lastUpdate" to userMediaProgress.lastUpdate,
            "duration" to userMediaProgress.duration
        )
    }
}
