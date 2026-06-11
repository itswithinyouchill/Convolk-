package com.example.data

import android.content.Context
import android.content.SharedPreferences

class ChatPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("chat_settings_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CUSTOM_API_KEY = "key_custom_api_key"
        private const val KEY_USE_CUSTOM_KEY = "key_use_custom_key"
        private const val KEY_SELECTED_MODEL = "key_selected_model"
        private const val DEFAULT_MODEL = "gemini-3.5-flash"
    }

    var customApiKey: String
        get() = prefs.getString(KEY_CUSTOM_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_CUSTOM_API_KEY, value).apply()

    var useCustomKey: Boolean
        get() = prefs.getBoolean(KEY_USE_CUSTOM_KEY, false)
        set(value) = prefs.edit().putBoolean(KEY_USE_CUSTOM_KEY, value).apply()

    var selectedModel: String
        get() = prefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL) ?: DEFAULT_MODEL
        set(value) = prefs.edit().putString(KEY_SELECTED_MODEL, value).apply()
}
