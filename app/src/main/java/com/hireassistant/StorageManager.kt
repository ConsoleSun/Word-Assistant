package com.hireassistant

import android.content.Context
import android.content.SharedPreferences

class StorageManager(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("hire_assistant", Context.MODE_PRIVATE)

    fun get(key: String, default: String): String = prefs.getString(key, default) ?: default
    fun put(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun getInt(key: String, default: Int): Int = prefs.getInt(key, default)
    fun putInt(key: String, value: Int) = prefs.edit().putInt(key, value).apply()
    fun remove(key: String) = prefs.edit().remove(key).apply()
    fun clear() = prefs.edit().clear().apply()
}
