package kaf.audiobookshelfwearos.app.userdata

import android.content.Context
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import timber.log.Timber
import java.io.File

@JsonIgnoreProperties(ignoreUnknown = true)
data class SideloadConfig(
    val protocol: String? = null,
    val serverAddress: String? = null,
    val login: String? = null,
    val password: String? = null,
    val jumpBackwardSeconds: Int? = null,
    val jumpForwardSeconds: Int? = null,
    val offlineMode: Boolean? = null,
    val smartDeleteEnabled: Boolean? = null,
    val smartDeleteMaxDownloads: Int? = null,
    val smartDeleteMaxBytes: Long? = null,
    val bezelMode: String? = null,
    val tapToPlayEnabled: Boolean? = null
)

/**
 * Pure result of merging a [SideloadConfig] against the currently-persisted credential
 * values. Credential-style fields (`protocol`/`serverAddress`/`login`/`password`) are
 * one-time secrets, so they're only populated here if the corresponding "current" value
 * passed in was empty. Every other field is a preference default, not a secret, so it
 * passes through unconditionally whenever present in the config -- a user may
 * legitimately want to re-push the config file to change any of them.
 *
 * Kept free of any Context/SharedPreferences dependency so the merge logic itself is
 * unit-testable without needing a real UserDataManager.
 */
data class ConfigApplicationResult(
    val protocol: String? = null,
    val serverAddress: String? = null,
    val login: String? = null,
    val password: String? = null,
    val jumpBackwardSeconds: Int? = null,
    val jumpForwardSeconds: Int? = null,
    val offlineMode: Boolean? = null,
    val smartDeleteEnabled: Boolean? = null,
    val smartDeleteMaxDownloads: Int? = null,
    val smartDeleteMaxBytes: Long? = null,
    val bezelMode: String? = null,
    val tapToPlayEnabled: Boolean? = null
) {
    val anyApplied: Boolean
        get() = serverAddress != null || login != null || password != null ||
            jumpBackwardSeconds != null || jumpForwardSeconds != null ||
            offlineMode != null || smartDeleteEnabled != null ||
            smartDeleteMaxDownloads != null || smartDeleteMaxBytes != null ||
            bezelMode != null || tapToPlayEnabled != null
}

/**
 * Lets a sideloaded build be pre-configured by pushing a JSON file to the app's own
 * external files dir (no special storage permission needed, and reachable via a plain
 * `adb push` right after install) instead of typing server/login details on the watch.
 */
object ConfigFileImporter {
    const val CONFIG_FILE_NAME = "shelftime-config.json"

    private val mapper = jacksonObjectMapper()

    /**
     * Pure merge logic: decides which fields from [config] should be applied given the
     * currently-persisted credential values. No Context/UserDataManager dependency, so
     * this is unit-testable directly.
     */
    fun resolveUpdates(
        config: SideloadConfig,
        currentServerAddress: String,
        currentLogin: String,
        currentPassword: String
    ): ConfigApplicationResult {
        var protocol: String? = null
        var serverAddress: String? = null
        var login: String? = null
        var password: String? = null

        if (currentServerAddress.isEmpty()) {
            protocol = config.protocol
            serverAddress = config.serverAddress
        }
        if (currentLogin.isEmpty()) {
            login = config.login
        }
        if (currentPassword.isEmpty()) {
            password = config.password
        }

        return ConfigApplicationResult(
            protocol = protocol,
            serverAddress = serverAddress,
            login = login,
            password = password,
            // Preference defaults, not one-time secrets -- apply unconditionally
            // whenever present, unlike the credential fields above.
            jumpBackwardSeconds = config.jumpBackwardSeconds,
            jumpForwardSeconds = config.jumpForwardSeconds,
            offlineMode = config.offlineMode,
            smartDeleteEnabled = config.smartDeleteEnabled,
            smartDeleteMaxDownloads = config.smartDeleteMaxDownloads,
            smartDeleteMaxBytes = config.smartDeleteMaxBytes,
            bezelMode = config.bezelMode,
            tapToPlayEnabled = config.tapToPlayEnabled
        )
    }

    /**
     * Applies the config file if present. Credential fields only fill in if the user
     * hasn't already set them (never overwrites an existing configuration); the jump-
     * seconds preference defaults apply unconditionally whenever present. Deletes the
     * file afterward either way, so the plaintext password doesn't linger on disk.
     *
     * @return true if any field was applied from the file.
     */
    fun importIfPresent(context: Context, userDataManager: UserDataManager): Boolean {
        val configFile = File(context.getExternalFilesDir(null), CONFIG_FILE_NAME)
        if (!configFile.exists()) return false

        return try {
            val config = mapper.readValue(configFile, SideloadConfig::class.java)
            val resolved = resolveUpdates(
                config,
                currentServerAddress = userDataManager.serverAddress,
                currentLogin = userDataManager.login,
                currentPassword = userDataManager.password
            )

            resolved.protocol?.let { userDataManager.protocol = it }
            resolved.serverAddress?.let { userDataManager.serverAddress = it }
            resolved.login?.let { userDataManager.login = it }
            resolved.password?.let { userDataManager.password = it }
            resolved.jumpBackwardSeconds?.let { userDataManager.jumpBackwardSeconds = it }
            resolved.jumpForwardSeconds?.let { userDataManager.jumpForwardSeconds = it }
            resolved.offlineMode?.let { userDataManager.offlineMode = it }
            resolved.smartDeleteEnabled?.let { userDataManager.smartDeleteEnabled = it }
            resolved.smartDeleteMaxDownloads?.let { userDataManager.smartDeleteMaxDownloads = it }
            resolved.smartDeleteMaxBytes?.let { userDataManager.smartDeleteMaxBytes = it }
            resolved.bezelMode?.let { userDataManager.bezelMode = it }
            resolved.tapToPlayEnabled?.let { userDataManager.tapToPlayEnabled = it }

            resolved.anyApplied
        } catch (e: Exception) {
            Timber.e(e, "Failed to import sideload config")
            false
        } finally {
            configFile.delete()
        }
    }
}
