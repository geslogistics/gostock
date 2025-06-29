package com.example.gostock // IMPORTANT: Replace with your actual package name

// Constants for DataWedge API actions and extras
object DataWedgeConstants {
    // General API Action for most DataWedge API calls
    const val ACTION_DATAWEDGE_API = "com.symbol.datawedge.api.ACTION"

    // Intent extras used for sending commands/configs to DataWedge
    const val EXTRA_SOFT_SCAN_TRIGGER = "com.symbol.datawedge.api.SOFT_SCAN_TRIGGER"
    const val EXTRA_SET_CONFIG = "com.symbol.datawedge.api.SET_CONFIG"
    const val EXTRA_CREATE_PROFILE = "com.symbol.datawedge.api.CREATE_PROFILE"
    const val EXTRA_ENABLE_PROFILE = "com.symbol.datawedge.api.ENABLE_PROFILE"
    const val EXTRA_SWITCH_TO_PROFILE = "com.symbol.datawedge.api.SWITCH_TO_PROFILE"
    const val EXTRA_SCAN_CONTROL = "com.symbol.datawedge.api.SCAN_CONTROL"

    // Actions and Extras for Notification API
    const val ACTION_REGISTER_FOR_NOTIFICATION = "com.symbol.datawedge.api.REGISTER_FOR_NOTIFICATION"
    const val ACTION_UNREGISTER_FOR_NOTIFICATION = "com.symbol.datawedge.api.UNREGISTER_FOR_NOTIFICATION"
    const val EXTRA_NOTIFICATION_BUNDLE = "NOTIFICATION"
    const val EXTRA_NOTIFICATION_TYPE_KEY_IN_BUNDLE = "NOTIFICATION_TYPE"

    // Actions and Extras for receiving data from DataWedge
    const val ACTION_RESULT_FROM_SCAN_DATA = "com.symbol.datawedge.api.RESULT_SCAN_DATA"
    const val ACTION_RESULT_NOTIFICATION = "com.symbol.datawedge.api.NOTIFICATION_ACTION"
    const val ACTION_RESULT = "com.symbol.datawedge.api.RESULT"

    // Corrected Intent extras for receiving data from DataWedge based on your Logcat
    const val EXTRA_SCANNED_DATA_STRING = "com.symbol.datawedge.data_string"
    const val EXTRA_SYMBOLOGY_TYPE_STRING = "com.symbol.datawedge.label_type"
    const val EXTRA_STATUS = "com.symbol.datawedge.api.STATUS"
    const val EXTRA_NOTIFICATION_STATUS = "com.symbol.datawedge.api.NOTIFICATION_STATUS"

    // Profile names (can be customized)
    const val PROFILE_NAME = "GoStockScanProfile"

    // Specific API Commands (values for EXTRA_SEND_COMMAND)
    const val EXTRA_SEND_COMMAND = "com.symbol.datawedge.api.SEND_COMMAND" // ADDED THIS CONSTANT
    const val START_SCANNING = "START_SCANNING"
    const val STOP_SCANNING = "STOP_SCANNING"
    const val ENABLE_PLUGIN = "ENABLE_PLUGIN"
    const val DISABLE_PLUGIN = "DISABLE_PLUGIN"
    const val ACTION_SET_ACTIVE_PROFILE = "com.symbol.datawedge.api.SET_ACTIVE_PROFILE"
    const val ACTION_SOFTSCANTRIGGER = "com.symbol.datawedge.api.SOFT_SCAN_TRIGGER"
    const val ACTION_SCANNERINPUTPLUGIN = "com.symbol.datawedge.api.SCANNER_INPUT_PLUGIN"

    // Profile configuration related
    const val CREATE_IF_NOT_EXIST = "CREATE_IF_NOT_EXIST"

    // Result handling constants
    const val EXTRA_COMMAND = "COMMAND"
    const val EXTRA_RESULT = "RESULT"
    const val EXTRA_COMMAND_IDENTIFIER = "COMMAND_IDENTIFIER"
    const val EXTRA_RESULT_INFO = "com.symbol.datawedge.api.RESULT_INFO"
    const val EXTRA_SEND_RESULT = "SEND_RESULT"
    const val SEND_RESULT_LAST_RESULT = "LAST_RESULT"
}