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
    val password: String? = null
)

/**
 * Lets a sideloaded build be pre-configured by pushing a JSON file to the app's own
 * external files dir (no special storage permission needed, and reachable via a plain
 * `adb push` right after install) instead of typing server/login details on the watch.
 */
object ConfigFileImporter {
    const val CONFIG_FILE_NAME = "shelftime-config.json"

    private val mapper = jacksonObjectMapper()

    /**
     * Applies the config file if present. Only fills in fields the user hasn't already
     * set (never overwrites an existing configuration), and deletes the file afterward
     * so the plaintext password doesn't linger on disk.
     *
     * @return true if any field was applied from the file.
     */
    fun importIfPresent(context: Context, userDataManager: UserDataManager): Boolean {
        val configFile = File(context.getExternalFilesDir(null), CONFIG_FILE_NAME)
        if (!configFile.exists()) return false

        return try {
            val config = mapper.readValue(configFile, SideloadConfig::class.java)
            var applied = false

            if (userDataManager.serverAddress.isEmpty()) {
                config.protocol?.let { userDataManager.protocol = it }
                config.serverAddress?.let {
                    userDataManager.serverAddress = it
                    applied = true
                }
            }
            if (userDataManager.login.isEmpty()) {
                config.login?.let {
                    userDataManager.login = it
                    applied = true
                }
            }
            if (userDataManager.password.isEmpty()) {
                config.password?.let {
                    userDataManager.password = it
                    applied = true
                }
            }

            applied
        } catch (e: Exception) {
            Timber.e(e, "Failed to import sideload config")
            false
        } finally {
            configFile.delete()
        }
    }
}
