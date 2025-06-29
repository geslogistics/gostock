package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.gostock.DataWedgeConstants // Correct import for your custom constants


// Callback interface for scan results
interface ZebraScanResultListener {
    fun onZebraScanResult(scanData: String?, symbology: String?) // MODIFIED: Added symbology
    fun onZebraScanError(errorMessage: String)
}

class ZebraScannerHelper(private val context: Context, private val listener: ZebraScanResultListener) {

    private val PROFILE_NAME = DataWedgeConstants.PROFILE_NAME
    private val TAG = "ZebraScannerHelper"

    // BroadcastReceiver for DataWedge scan results
    private val datawedgeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val receivedIntent = intent ?: return
            val receivedContext = context ?: return

            if (receivedIntent.action == receivedContext.resources.getString(R.string.activity_intent_filter_action)) {
                if (receivedIntent.hasExtra(DataWedgeConstants.EXTRA_SCANNED_DATA_STRING)) {
                    val scanData = receivedIntent.getStringExtra(DataWedgeConstants.EXTRA_SCANNED_DATA_STRING)
                    val symbology = receivedIntent.getStringExtra(DataWedgeConstants.EXTRA_SYMBOLOGY_TYPE_STRING) // NEW: Get symbology
                    Log.d(TAG, "DataWedge scanned: $scanData (Symbology: $symbology)")
                    listener.onZebraScanResult(scanData, symbology) // Pass both to listener
                } else {
                    Log.w(TAG, "DataWedge scan intent received but no scan data found.")
                    listener.onZebraScanError("No scan data received from scanner.")
                }
            } else {
                Log.d(TAG, "Received unknown intent action: ${receivedIntent.action}")
            }
        }
    }

    /** Initializes and configures the DataWedge profile. */
    fun setupDataWedgeProfile() {
        val bMain = Bundle() // Main bundle for SET_CONFIG payload
        val bConfig = Bundle() // Bundle for PLUGIN_CONFIG
        val bParams = Bundle() // Bundle for Intent plugin parameters

        // 1. Configure Intent Output Plugin Parameters
        bParams.putString("intent_output_enabled", "true")
        // Use YOUR APP'S intent action for data delivery
        bParams.putString("intent_action", context.resources.getString(R.string.activity_intent_filter_action))
        // Explicitly set the category for the intent. This is crucial for matching your receiver.
        bParams.putString("intent_category", Intent.CATEGORY_DEFAULT)
        // Set delivery method as Integer (2 for Broadcast) as per reference
        bParams.putInt("intent_delivery", 2) // 0 for Start Activity, 1 for Start Service, 2 for Broadcast

        // 2. Configure Intent Plugin itself
        bConfig.putString("PLUGIN_NAME", "INTENT")
        bConfig.putString("RESET_CONFIG", "false") // Set to false as per reference
        bConfig.putBundle("PARAM_LIST", bParams) // Add parameters to the plugin config

        // 3. Configure the main Profile
        bMain.putString("PROFILE_NAME", DataWedgeConstants.PROFILE_NAME)
        bMain.putString("PROFILE_ENABLED", "true")
        // Use CREATE_IF_NOT_EXIST for CONFIG_MODE as per reference
        bMain.putString("CONFIG_MODE", "CREATE_IF_NOT_EXIST")
        bMain.putBundle("PLUGIN_CONFIG", bConfig) // Add plugin config to main profile bundle

        // Associated apps: Link your application to this DataWedge profile.
        // DataWedge will only process scans for apps listed here when this profile is active.
        val appConfig = Bundle()
        appConfig.putString("PACKAGE_NAME", "com.example.gostock") // Set your app's package name
        appConfig.putStringArray("ACTIVITY_LIST", arrayOf("*")) // Apply to all activities in this package
        bMain.putParcelableArray("APP_LIST", arrayOf(appConfig))


        // 4. Send the SET_CONFIG Intent
        val i = Intent()
        i.action = DataWedgeConstants.ACTION_DATAWEDGE_API // Use the general API action
        i.putExtra(DataWedgeConstants.EXTRA_SET_CONFIG, bMain) // Pass the full profile configuration bundle
        i.putExtra(DataWedgeConstants.EXTRA_COMMAND_IDENTIFIER, "SET_CONFIG_$PROFILE_NAME") // Unique ID for this command
        i.putExtra(DataWedgeConstants.EXTRA_SEND_RESULT, DataWedgeConstants.SEND_RESULT_LAST_RESULT) // Use "LAST_RESULT" for robust result feedback

        context.sendBroadcast(i)
        Log.d("DataWedge", "Sent SET_CONFIG intent for profile: ${DataWedgeConstants.PROFILE_NAME}")
    }

    /** Registers the BroadcastReceiver for DataWedge scanned data. */
    fun registerReceiver() {
        val filter = IntentFilter()
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        filter.addAction(context.resources.getString(R.string.activity_intent_filter_action))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.registerReceiver(context, datawedgeReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        } else {
            context.registerReceiver(datawedgeReceiver, filter)
        }
        Log.d(TAG, "DataWedge receiver registered.")
    }

    /** Unregisters the BroadcastReceiver. */
    fun unregisterReceiver() {
        try {
            context.unregisterReceiver(datawedgeReceiver)
            Log.d(TAG, "DataWedge receiver unregistered.")
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Receiver was already unregistered: ${e.message}")
        }
    }

    /** Sends a command to the DataWedge service. */
    fun sendDataWedgeCommand(action: String, command: String) {
        val i = Intent()
        i.action = action
        i.putExtra(DataWedgeConstants.EXTRA_SEND_COMMAND, command)
        context.sendBroadcast(i)
        Log.d(TAG, "Sent DataWedge command: $action - $command")
    }

    /** Activates a specific DataWedge profile. */
    fun activateProfile(profileName: String) {
        sendDataWedgeCommand(DataWedgeConstants.ACTION_SET_ACTIVE_PROFILE, profileName)
        Log.d(TAG, "Activated DataWedge profile: $profileName")
    }

    /** Starts the soft scan trigger. */
    fun startSoftScan() {
        sendDataWedgeCommand(DataWedgeConstants.ACTION_SOFTSCANTRIGGER, DataWedgeConstants.START_SCANNING)
        Log.d(TAG, "Soft scan initiated.")
    }

    /** Stops the soft scan trigger. */
    fun stopSoftScan() {
        sendDataWedgeCommand(DataWedgeConstants.ACTION_SOFTSCANTRIGGER, DataWedgeConstants.STOP_SCANNING)
        Log.d(TAG, "Soft scan stopped.")
    }

    /** Enables the barcode plugin. */
    fun enableBarcodePlugin() {
        sendDataWedgeCommand(DataWedgeConstants.ACTION_SCANNERINPUTPLUGIN, DataWedgeConstants.ENABLE_PLUGIN)
        Log.d(TAG, "Barcode plugin enabled.")
    }

    /** Disables the barcode plugin. */
    fun disableBarcodePlugin() {
        sendDataWedgeCommand(DataWedgeConstants.ACTION_SCANNERINPUTPLUGIN, DataWedgeConstants.DISABLE_PLUGIN)
        Log.d(TAG, "Barcode plugin disabled.")
    }
}