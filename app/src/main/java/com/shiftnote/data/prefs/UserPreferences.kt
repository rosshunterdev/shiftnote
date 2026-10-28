package com.shiftnote.data.prefs

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.shiftnote.inference.ImproveStyle

class UserPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("shiftnote_prefs", Context.MODE_PRIVATE)

    private val securePrefs: SharedPreferences = createSecurePrefs(context)

    init {
        // The editable full prompt was replaced by a fixed core prompt plus
        // extra instructions. Drop any saved copy so it can't linger.
        prefs.edit().remove(LEGACY_KEY_SYSTEM_PROMPT).apply()
    }

    var role: String
        get() = prefs.getString(KEY_ROLE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_ROLE, value).apply()

    var generalContext: String
        get() = prefs.getString(KEY_GENERAL_CONTEXT, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GENERAL_CONTEXT, value).apply()

    var improveStyle: ImproveStyle
        get() = ImproveStyle.fromStored(prefs.getString(KEY_IMPROVE_STYLE, null))
        set(value) = prefs.edit().putString(KEY_IMPROVE_STYLE, value.name).apply()

    var extraInstructions: String
        get() = prefs.getString(KEY_EXTRA_INSTRUCTIONS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_EXTRA_INSTRUCTIONS, value).apply()

    var selectedClientId: Long
        get() = prefs.getLong(KEY_SELECTED_CLIENT, -1L)
        set(value) = prefs.edit().putLong(KEY_SELECTED_CLIENT, value).apply()

    var inferenceMode: String
        get() = prefs.getString(KEY_INFERENCE_MODE, MODE_ON_DEVICE) ?: MODE_ON_DEVICE
        set(value) = prefs.edit().putString(KEY_INFERENCE_MODE, value).apply()

    var apiKey: String
        get() = securePrefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = securePrefs.edit().putString(KEY_API_KEY, value).apply()

    private fun createSecurePrefs(context: Context): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            "shiftnote_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    companion object {
        private const val KEY_ROLE = "role"
        private const val KEY_GENERAL_CONTEXT = "general_context"
        private const val LEGACY_KEY_SYSTEM_PROMPT = "system_prompt"
        private const val KEY_IMPROVE_STYLE = "improve_style"
        private const val KEY_EXTRA_INSTRUCTIONS = "extra_instructions"
        private const val KEY_SELECTED_CLIENT = "selected_client_id"
        private const val KEY_INFERENCE_MODE = "inference_mode"
        private const val KEY_API_KEY = "api_key"
        const val MODE_ON_DEVICE = "on_device"
        const val MODE_CLOUD = "cloud"
    }
}
