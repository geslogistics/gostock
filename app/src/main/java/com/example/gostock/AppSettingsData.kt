package com.example.gostock

// A simple data class to hold all settings for serialization.
// This makes it easy to send/receive all settings as a single JSON object.
data class AppSettingsData(
    val maxBatchSize: Int,
    val maxBatchTime: Int,
    val enableZebraDevice: Boolean,
    val acceptedLocationFormats: Set<String>,
    val locationRequired: Boolean,
    val locationEditable: Boolean,
    val acceptedSkuFormats: Set<String>,
    val skuRequired: Boolean,
    val skuEditable: Boolean
)
