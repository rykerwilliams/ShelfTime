package kaf.audiobookshelfwearos.app.userdata

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class UserDataManager(context: Context) {

    companion object {
        private const val PREF_NAME = "user_data"
        private const val KEY_SPEED = "speed"
        private const val KEY_SERVER_ADDRESS = "server_address"
        private const val KEY_LOGIN = "login"
        private const val KEY_PASSWORD = "password"
        private const val KEY_PROTOCOL = "protocol"
        private const val KEY_TOKEN = "token"
        private const val KEY_USERID = "userid"
        private const val KEY_OFFLINEMODE = "offlinemode"
        private const val KEY_SMART_DELETE_ENABLED = "smart_delete_enabled"
        private const val KEY_SMART_DELETE_MAX_DOWNLOADS = "smart_delete_max_downloads"
        private const val KEY_SMART_DELETE_MAX_BYTES = "smart_delete_max_bytes"
        private const val KEY_JUMP_BACKWARD_SECONDS = "jump_backward_seconds"
        private const val KEY_JUMP_FORWARD_SECONDS = "jump_forward_seconds"
        private const val KEY_BEZEL_MODE = "bezel_mode"
        private const val KEY_TAP_TO_PLAY_ENABLED = "tap_to_play_enabled"

        // ~2GB default byte budget for Smart Delete (see §8, UI_CHANGES_PLAN.md).
        private const val DEFAULT_SMART_DELETE_MAX_BYTES = 2_000_000_000L
    }

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        PREF_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var serverAddress: String
        get() = sharedPreferences.getString(KEY_SERVER_ADDRESS, null) ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_SERVER_ADDRESS, value).apply()

    var login: String
        get() = sharedPreferences.getString(KEY_LOGIN, null) ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_LOGIN, value).apply()

    var password: String
        get() = sharedPreferences.getString(KEY_PASSWORD, null) ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_PASSWORD, value).apply()

    var userId: String
        get() = sharedPreferences.getString(KEY_USERID, null) ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_USERID, value).apply()

    var token: String
        get() = sharedPreferences.getString(KEY_TOKEN, null) ?: ""
        set(value) = sharedPreferences.edit().putString(KEY_TOKEN, value).apply()

    var speed: Float
        get() = sharedPreferences.getFloat(KEY_SPEED, 1f)
        set(value) = sharedPreferences.edit().putFloat(KEY_SPEED, value).apply()

    var protocol: String
        get() = sharedPreferences.getString(KEY_PROTOCOL, null) ?: "https"
        set(value) = sharedPreferences.edit().putString(KEY_PROTOCOL, value).apply()

    var offlineMode: Boolean
        get() = sharedPreferences.getBoolean(KEY_OFFLINEMODE, false)
        set(value) = sharedPreferences.edit().putBoolean(KEY_OFFLINEMODE, value).apply()

    var smartDeleteEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_SMART_DELETE_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SMART_DELETE_ENABLED, value).apply()

    var smartDeleteMaxDownloads: Int
        get() = sharedPreferences.getInt(KEY_SMART_DELETE_MAX_DOWNLOADS, 5)
        set(value) = sharedPreferences.edit().putInt(KEY_SMART_DELETE_MAX_DOWNLOADS, value).apply()

    var smartDeleteMaxBytes: Long
        get() = sharedPreferences.getLong(KEY_SMART_DELETE_MAX_BYTES, DEFAULT_SMART_DELETE_MAX_BYTES)
        set(value) = sharedPreferences.edit().putLong(KEY_SMART_DELETE_MAX_BYTES, value).apply()

    var jumpBackwardSeconds: Int
        get() = sharedPreferences.getInt(KEY_JUMP_BACKWARD_SECONDS, 10)
        set(value) = sharedPreferences.edit().putInt(KEY_JUMP_BACKWARD_SECONDS, value).apply()

    var jumpForwardSeconds: Int
        get() = sharedPreferences.getInt(KEY_JUMP_FORWARD_SECONDS, 30)
        set(value) = sharedPreferences.edit().putInt(KEY_JUMP_FORWARD_SECONDS, value).apply()

    var bezelMode: String
        get() = sharedPreferences.getString(KEY_BEZEL_MODE, null) ?: "Scrub"
        set(value) = sharedPreferences.edit().putString(KEY_BEZEL_MODE, value).apply()

    // Default true: preserves the existing "tap a book to jump straight into
    // playback when nothing's playing" behavior for anyone upgrading.
    var tapToPlayEnabled: Boolean
        get() = sharedPreferences.getBoolean(KEY_TAP_TO_PLAY_ENABLED, true)
        set(value) = sharedPreferences.edit().putBoolean(KEY_TAP_TO_PLAY_ENABLED, value).apply()

    fun clearUserData() {
        sharedPreferences.edit().clear().apply()
    }

    fun getCompleteAddress(): String {
        return "$protocol://$serverAddress"
    }
}