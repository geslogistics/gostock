package com.example.gostock

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Locale

object AppSettings {

    private const val PREFS_NAME = "app_settings_prefs"
    private const val KEY_MAX_BATCH_SIZE = "max_batch_size"
    private const val KEY_MAX_BATCH_TIME = "max_batch_time"
    private const val KEY_ENABLE_ZEBRA_DEVICE = "enable_zebra_device"

    private const val KEY_ACCEPTED_LOCATION_FORMATS = "accepted_location_formats"
    private const val KEY_LOCATION_REQUIRED = "location_required"
    private const val KEY_LOCATION_EDITABLE = "location_editable"

    private const val KEY_ACCEPTED_SKU_FORMATS = "accepted_sku_formats"
    private const val KEY_SKU_REQUIRED = "sku_required"
    private const val KEY_SKU_EDITABLE = "sku_editable"

    // Default values
    const val DEFAULT_MAX_BATCH_SIZE = 100 // count
    const val DEFAULT_MAX_BATCH_TIME = 2 // hours
    const val DEFAULT_ENABLE_ZEBRA_DEVICE = false
    const val DEFAULT_LOCATION_REQUIRED = true
    const val DEFAULT_LOCATION_EDITABLE = false
    const val DEFAULT_SKU_REQUIRED = true
    const val DEFAULT_SKU_EDITABLE = false

    // --- NEW: Specific default formats ---
    private val DEFAULT_LOCATION_FORMATS = setOf("LABEL-TYPE-DATAMATRIX", "DATA_MATRIX")
    private val DEFAULT_SKU_FORMATS = setOf("LABEL-TYPE-EAN13", "EAN_13")

    private lateinit var sharedPreferences: SharedPreferences
    private val gson = Gson()

    fun initialize(context: Context) {
        sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    var maxBatchSize: Int
        get() = sharedPreferences.getInt(KEY_MAX_BATCH_SIZE, DEFAULT_MAX_BATCH_SIZE)
        set(value) = sharedPreferences.edit().putInt(KEY_MAX_BATCH_SIZE, value).apply()

    var maxBatchTime: Int
        get() = sharedPreferences.getInt(KEY_MAX_BATCH_TIME, DEFAULT_MAX_BATCH_TIME)
        set(value) = sharedPreferences.edit().putInt(KEY_MAX_BATCH_TIME, value).apply()

    var enableZebraDevice: Boolean
        get() = sharedPreferences.getBoolean(KEY_ENABLE_ZEBRA_DEVICE, DEFAULT_ENABLE_ZEBRA_DEVICE)
        set(value) = sharedPreferences.edit().putBoolean(KEY_ENABLE_ZEBRA_DEVICE, value).apply()

    var acceptedLocationFormats: Set<String>
        get() {
            val json = sharedPreferences.getString(KEY_ACCEPTED_LOCATION_FORMATS, null)
            return if (json != null) {
                // If a value is saved in preferences, use it.
                gson.fromJson(json, object : TypeToken<Set<String>>() {}.type) ?: DEFAULT_LOCATION_FORMATS
            } else {
                // --- CHANGE: Use the new specific default ---
                DEFAULT_LOCATION_FORMATS
            }
        }
        set(value) {
            val json = gson.toJson(value)
            sharedPreferences.edit().putString(KEY_ACCEPTED_LOCATION_FORMATS, json).apply()
        }

    var locationRequired: Boolean
        get() = sharedPreferences.getBoolean(KEY_LOCATION_REQUIRED, DEFAULT_LOCATION_REQUIRED)
        set(value) = sharedPreferences.edit().putBoolean(KEY_LOCATION_REQUIRED, value).apply()

    var locationEditable: Boolean
        get() = sharedPreferences.getBoolean(KEY_LOCATION_EDITABLE, DEFAULT_LOCATION_EDITABLE)
        set(value) = sharedPreferences.edit().putBoolean(KEY_LOCATION_EDITABLE, value).apply()

    var acceptedSkuFormats: Set<String>
        get() {
            val json = sharedPreferences.getString(KEY_ACCEPTED_SKU_FORMATS, null)
            return if (json != null) {
                // If a value is saved in preferences, use it.
                gson.fromJson(json, object : TypeToken<Set<String>>() {}.type) ?: DEFAULT_SKU_FORMATS
            } else {
                // --- CHANGE: Use the new specific default ---
                DEFAULT_SKU_FORMATS
            }
        }
        set(value) {
            val json = gson.toJson(value)
            sharedPreferences.edit().putString(KEY_ACCEPTED_SKU_FORMATS, json).apply()
        }

    var skuRequired: Boolean
        get() = sharedPreferences.getBoolean(KEY_SKU_REQUIRED, DEFAULT_SKU_REQUIRED)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SKU_REQUIRED, value).apply()

    var skuEditable: Boolean
        get() = sharedPreferences.getBoolean(KEY_SKU_EDITABLE, DEFAULT_SKU_EDITABLE)
        set(value) = sharedPreferences.edit().putBoolean(KEY_SKU_EDITABLE, value).apply()

    fun isFormatAccepted(scannedFormat: String?, acceptedFormats: Set<String>): Boolean {
        if (scannedFormat.isNullOrEmpty()) {
            return false
        }
        if (acceptedFormats.isEmpty()) {
            return true
        }
        return acceptedFormats.any { it.equals(scannedFormat, ignoreCase = true) }
    }
}
