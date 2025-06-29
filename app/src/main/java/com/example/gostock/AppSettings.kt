package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.content.Context
import android.content.SharedPreferences

object AppSettings {

    private const val PREFS_NAME = "app_settings_prefs"
    private const val KEY_MAX_BATCH_SIZE = "max_batch_size"
    private const val KEY_MAX_BATCH_TIME = "max_batch_time"
    private const val KEY_ENABLE_ZEBRA_DEVICE = "enable_zebra_device"


    // Default values
    const val DEFAULT_MAX_BATCH_SIZE = 0 // count
    const val DEFAULT_MAX_BATCH_TIME = 0 // hours
    const val DEFAULT_ENABLE_ZEBRA_DEVICE = false

    private lateinit var sharedPreferences: SharedPreferences

    // Initialize this once, typically in your Application class (GoStockApp)
    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var maxBatchSize: Int
        get() = sharedPreferences.getInt(KEY_MAX_BATCH_SIZE, DEFAULT_MAX_BATCH_SIZE)
        set(value) = sharedPreferences.edit().putInt(KEY_MAX_BATCH_SIZE, value).apply()

    var maxBatchTime: Int
        get() = sharedPreferences.getInt(KEY_MAX_BATCH_TIME, DEFAULT_MAX_BATCH_TIME)
        set(value) = sharedPreferences.edit().putInt(KEY_MAX_BATCH_TIME, value).apply()

    var enableZebraDevice: Boolean // NEW PROPERTY
        get() = sharedPreferences.getBoolean(KEY_ENABLE_ZEBRA_DEVICE, DEFAULT_ENABLE_ZEBRA_DEVICE)
        set(value) = sharedPreferences.edit().putBoolean(KEY_ENABLE_ZEBRA_DEVICE, value).apply()

}