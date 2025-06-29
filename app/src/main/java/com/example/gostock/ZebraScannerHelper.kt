package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.content.ContextCompat


// Callback interface for scan results
interface ZebraScanResultListener {
    fun onZebraScanResult(scanData: String?)
    fun onZebraScanError(errorMessage: String)
}

class ZebraScannerHelper(private val context: Context, private val listener: ZebraScanResultListener) {

    private val PROFILE_NAME = DataWedgeConstants.PROFILE_NAME // Use constant
    private val TAG = "ZebraScannerHelper"

    // BroadcastReceiver for DataWedge scan results
    private val datawedgeReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val receivedIntent = intent ?: return
            val receivedContext = context ?: return

            if (receivedIntent.action == receivedContext.resources.getString(R.string.activity_intent_filter_action)) {
                if (receivedIntent.hasExtra(DataWedgeConstants.EXTRA_SCANNED_DATA_STRING)) { // Use DataWedgeConstants
                    val scanData = receivedIntent.getStringExtra(DataWedgeConstants.EXTRA_SCANNED_DATA_STRING) // Use DataWedgeConstants
                    Log.d(TAG, "DataWedge scanned: $scanData")
                    listener.onZebraScanResult(scanData)
                } else {
                    Log.w(TAG, "DataWedge scan intent received but no scan data found.")
                    listener.onZebraScanError("No scan data received from scanner.")
                }
            }
        }
    }

    /** Initializes and configures the DataWedge profile. */
    fun setupDataWedgeProfile() {
        Log.d(TAG, "Configuring DataWedge profile '$PROFILE_NAME'...")

        val bMain = Bundle()
        val bConfig = Bundle()
        val bParams = Bundle()

        // 1. Configure Intent Output Plugin Parameters
        bParams.putString("intent_output_enabled", "true")
        bParams.putString("intent_action", context.resources.getString(R.string.activity_intent_filter_action))
        bParams.putString("intent_category", Intent.CATEGORY_DEFAULT)
        bParams.putInt("intent_delivery", 2)
        bConfig.putString("PLUGIN_NAME", "INTENT")
        bConfig.putString("RESET_CONFIG", "false")
        bConfig.putBundle("PARAM_LIST", bParams)

        // 2. Configure Barcode Plugin (Scanner)
        val barcodeConfig = Bundle()
        barcodeConfig.putString("PLUGIN_NAME", "BARCODE")
        barcodeConfig.putString("RESET_CONFIG", "false")
        val barcodeParams = Bundle()
        barcodeParams.putString("scanner_selection", "auto")
        barcodeParams.putString("scanner_input_enabled", "true")
        barcodeConfig.putBundle("PARAM_LIST", barcodeParams)


        // 3. Configure the main Profile
        bMain.putString("PROFILE_NAME", PROFILE_NAME)
        bMain.putString("PROFILE_ENABLED", "true")
        bMain.putString("CONFIG_MODE", DataWedgeConstants.CREATE_IF_NOT_EXIST) // Use DataWedgeConstants
        bMain.putBundle("PLUGIN_CONFIG", barcodeConfig)
        // It's common to explicitly list both intent and barcode plugins if they are separate configs
        bMain.putParcelableArray("PLUGIN_CONFIG_INTENT", arrayOf(bConfig)) // Add Intent plugin as well

        bMain.putParcelableArray("APP_LIST", arrayOf(
            Bundle().apply {
                putString("PACKAGE_NAME", context.packageName)
                putStringArray("ACTIVITY_LIST", arrayOf("*"))
            }
        ))
        bMain.putParcelableArray("ASSOCIATED_APPS", arrayOf(
            Bundle().apply {
                putString("PACKAGE_NAME", context.packageName)
                putStringArray("ACTIVITY_LIST", arrayOf("*"))
            }
        ))


        // 4. Send the SET_CONFIG Intent
        val i = Intent()
        i.action = DataWedgeConstants.ACTION_DATAWEDGE_API // Use DataWedgeConstants
        i.putExtra(DataWedgeConstants.EXTRA_SET_CONFIG, bMain) // Use DataWedgeConstants
        i.putExtra(DataWedgeConstants.EXTRA_COMMAND_IDENTIFIER, "SET_CONFIG_$PROFILE_NAME") // Use DataWedgeConstants
        i.putExtra(DataWedgeConstants.EXTRA_SEND_RESULT, "true") // Use DataWedgeConstants

        context.sendBroadcast(i)
        Log.d(TAG, "Sent SET_CONFIG intent for profile: $PROFILE_NAME")
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
        i.putExtra(DataWedgeConstants.EXTRA_SEND_COMMAND, command) // Use DataWedgeConstants
        context.sendBroadcast(i)
        Log.d(TAG, "Sent DataWedge command: $action - $command")
    }

    /** Activates a specific DataWedge profile. */
    fun activateProfile(profileName: String) {
        sendDataWedgeCommand(DataWedgeConstants.ACTION_SET_ACTIVE_PROFILE, profileName) // Use DataWedgeConstants
        Log.d(TAG, "Activated DataWedge profile: $profileName")
    }

    /** Starts the soft scan trigger. */
    fun startSoftScan() {
        sendDataWedgeCommand(DataWedgeConstants.ACTION_SOFTSCANTRIGGER, DataWedgeConstants.START_SCANNING) // Use DataWedgeConstants
        Log.d(TAG, "Soft scan initiated.")
    }

    /** Stops the soft scan trigger. */
    fun stopSoftScan() {
        sendDataWedgeCommand(DataWedgeConstants.ACTION_SOFTSCANTRIGGER, DataWedgeConstants.STOP_SCANNING) // Use DataWedgeConstants
        Log.d(TAG, "Soft scan stopped.")
    }

    /** Enables the barcode plugin. */
    fun enableBarcodePlugin() {
        sendDataWedgeCommand(DataWedgeConstants.ACTION_SCANNERINPUTPLUGIN, DataWedgeConstants.ENABLE_PLUGIN) // Use DataWedgeConstants
        Log.d(TAG, "Barcode plugin enabled.")
    }

    /** Disables the barcode plugin. */
    fun disableBarcodePlugin() {
        sendDataWedgeCommand(DataWedgeConstants.ACTION_SCANNERINPUTPLUGIN, DataWedgeConstants.DISABLE_PLUGIN) // Use DataWedgeConstants
        Log.d(TAG, "Barcode plugin disabled.")
    }
}