package com.example.gostock // Your app's package name

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat // Import for ContextCompat

// Constants for DataWedge API actions and extras
object DataWedgeConstantsOld {
    // General API Action for most DataWedge API calls
    const val ACTION_DATAWEDGE_API = "com.symbol.datawedge.api.ACTION"

    // Intent extras used for sending commands/configs to DataWedge
    const val EXTRA_SOFT_SCAN_TRIGGER = "com.symbol.datawedge.api.SOFT_SCAN_TRIGGER"
    const val EXTRA_SET_CONFIG = "com.symbol.datawedge.api.SET_CONFIG"
    const val EXTRA_CREATE_PROFILE = "com.symbol.datawedge.api.CREATE_PROFILE"
    const val EXTRA_ENABLE_PROFILE = "com.symbol.datawedge.api.ENABLE_PROFILE"
    const val EXTRA_SWITCH_TO_PROFILE = "com.symbol.datawedge.api.SWITCH_TO_PROFILE"
    const val EXTRA_SCAN_CONTROL = "com.symbol.datawedge.api.SCAN_CONTROL"

    // Actions and Extras for Notification API (Register/Unregister for Notifications)
    const val ACTION_REGISTER_FOR_NOTIFICATION = "com.symbol.datawedge.api.REGISTER_FOR_NOTIFICATION"
    const val ACTION_UNREGISTER_FOR_NOTIFICATION = "com.symbol.datawedge.api.UNREGISTER_FOR_NOTIFICATION"
    const val EXTRA_NOTIFICATION_BUNDLE = "NOTIFICATION"
    const val EXTRA_NOTIFICATION_TYPE_KEY_IN_BUNDLE = "NOTIFICATION_TYPE"


    // Intents/Actions for receiving data from DataWedge
    const val ACTION_RESULT_FROM_SCANNER = "com.symbol.datawedge.api.RESULT_ACTION" // For profile configuration results
    const val ACTION_RESULT_NOTIFICATION = "com.symbol.datawedge.api.NOTIFICATION_ACTION" // For receiving notifications (e.g., scanner status)

    // CORRECTED Intent extras for receiving data from DataWedge based on your Logcat
    const val EXTRA_SCANNED_DATA_STRING = "com.symbol.datawedge.data_string" // Corrected key for barcode data
    const val EXTRA_SYMBOLOGY_TYPE_STRING = "com.symbol.datawedge.label_type" // Corrected key for symbology type
    // Removed EXTRA_DECODED_DATA_ID as it's a byte array and not used for direct string extraction here.
    const val EXTRA_STATUS = "com.symbol.datawedge.api.STATUS" // For result_action status
    const val EXTRA_NOTIFICATION_STATUS = "com.symbol.datawedge.api.NOTIFICATION_STATUS" // For received notification status (bundle key)

    // Profile names (can be customized)
    const val PROFILE_NAME = "GoStock Profile" // Using your defined profile name

    // Action used to request results from DataWedge API calls (e.g., SET_CONFIG result)
    const val ACTION_RESULT = "com.symbol.datawedge.api.RESULT"
    const val EXTRA_RESULT_INFO = "com.symbol.datawedge.api.RESULT_INFO"
    const val EXTRA_COMMAND = "COMMAND"
    const val EXTRA_COMMAND_IDENTIFIER = "COMMAND_IDENTIFIER"
    const val EXTRA_RESULT = "RESULT"
    // Updated constant for requesting results (from reference)
    const val EXTRA_SEND_RESULT = "SEND_RESULT" // This is the key
    const val SEND_RESULT_LAST_RESULT = "LAST_RESULT" // This is the value
    const val SEND_RESULT_TRUE = "true" // For older DW versions if needed
}

class SubActivity : AppCompatActivity() {

    private lateinit var scannedDataTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var softScanButton: Button

    // BroadcastReceiver to receive scanned data and scanner status updates
    private val dataWedgeBroadcastReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent?.apply {
                when (action) {
                    // Action for receiving scanned barcode data. This action string must match the one configured in DataWedge profile.
                    // It should also match the action defined in your strings.xml resource.
                    resources.getString(R.string.activity_intent_filter_action) -> {
                        // Log all extras from the scanned barcode intent for debugging
                        Log.d("DataWedge", "--- Barcode Data Intent Received ---")
                        val extrasBundle = extras
                        if (extrasBundle != null) {
                            for (key in extrasBundle.keySet()) {
                                Log.d("DataWedge", "  Extra: $key = ${extrasBundle.get(key)}")
                            }
                        }
                        Log.d("DataWedge", "----------------------------------")

                        // CORRECTED: Directly extract the barcode data string and symbology string
                        val barcodeData: String? = getStringExtra(DataWedgeConstantsOld.EXTRA_SCANNED_DATA_STRING)
                        val symbology: String? = getStringExtra(DataWedgeConstantsOld.EXTRA_SYMBOLOGY_TYPE_STRING)

                        if (!barcodeData.isNullOrBlank()) {
                            val displayText = "Scanned: $barcodeData\nSymbology: $symbology"
                            scannedDataTextView.text = displayText
                            Log.d("DataWedge", "Displayed Scanned Data: $barcodeData, Symbology: $symbology")
                        } else {
                            Log.w("DataWedge", "Received empty or null barcode data after extraction.")
                            scannedDataTextView.text = "No barcode data received."
                        }
                    }
                    // Action for receiving scanner status notifications
                    DataWedgeConstantsOld.ACTION_RESULT_NOTIFICATION -> {
                        if (hasExtra(DataWedgeConstantsOld.EXTRA_NOTIFICATION_STATUS)) {
                            val bundle = getBundleExtra(DataWedgeConstantsOld.EXTRA_NOTIFICATION_STATUS)
                            val status = bundle?.getString("SCANNER_STATUS")
                            val profile = bundle?.getString("PROFILE_NAME")
                            val displayText = "Scanner Status: $status\nProfile: $profile"
                            statusTextView.text = displayText
                            Log.d("DataWedge", "Scanner Status: $status, Profile: $profile")
                        }
                    }
                    // Action for receiving results of DataWedge API commands (e.g., SET_CONFIG)
                    DataWedgeConstantsOld.ACTION_RESULT -> {
                        val command: String? = getStringExtra(DataWedgeConstantsOld.EXTRA_COMMAND)
                        val result: String? = getStringExtra(DataWedgeConstantsOld.EXTRA_RESULT)
                        val commandIdentifier: String? = getStringExtra(DataWedgeConstantsOld.EXTRA_COMMAND_IDENTIFIER) // Added CID

                        val resultInfo: Bundle? = getBundleExtra(DataWedgeConstantsOld.EXTRA_RESULT_INFO)

                        // Log all available result info for debugging
                        Log.d("DataWedge", "--- API Result Received ---")
                        Log.d("DataWedge", "  Command: $command")
                        Log.d("DataWedge", "  Result: $result")
                        Log.d("DataWedge", "  Command Identifier (CID): $commandIdentifier")
                        Log.d("DataWedge", "  Result Info Bundle: $resultInfo")
                        if (resultInfo != null) {
                            for (key in resultInfo.keySet()) {
                                Log.d("DataWedge", "    $key = ${resultInfo.get(key)}")
                            }
                        }
                        Log.d("DataWedge", "---------------------------")


                        if (command == DataWedgeConstantsOld.EXTRA_SET_CONFIG) { // Note: The 'COMMAND' extra returns the full API name
                            val configStatus = resultInfo?.getString("RESULT_CODE") ?: "UNKNOWN"
                            if (result == "FAILURE") {
                                val errorMessage = resultInfo?.getString("ERROR_MESSAGE") ?: "No error message provided."
                                Log.e("DataWedge", "SET_CONFIG Failed. Error Info: $errorMessage")
                                statusTextView.text = "Config Failed: $errorMessage"
                            } else {
                                statusTextView.text = "Config Applied Successfully."
                                Log.i("DataWedge", "SET_CONFIG successful for profile ${DataWedgeConstantsOld.PROFILE_NAME}.")
                            }
                        } else if (command == DataWedgeConstantsOld.EXTRA_CREATE_PROFILE) { // Note: The 'COMMAND' extra returns the full API name
                            val createStatus = resultInfo?.getString("RESULT_CODE") ?: "UNKNOWN"
                            if (result == "FAILURE") {
                                val errorMessage = resultInfo?.getString("ERROR_MESSAGE") ?: "No error message provided."
                                Log.e("DataWedge", "CREATE_PROFILE Failed. Error Info: $errorMessage")
                                statusTextView.text = "Profile Creation Failed: $errorMessage"
                            } else {
                                Log.i("DataWedge", "CREATE_PROFILE successful for profile ${DataWedgeConstantsOld.PROFILE_NAME}.")
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sub) // Changed layout file from activity_main to activity_sub

        // Initialize UI elements by their IDs defined in activity_sub.xml
        scannedDataTextView = findViewById(R.id.scannedDataTextView)
        statusTextView = findViewById(R.id.statusTextView)
        softScanButton = findViewById(R.id.softScanButton)

        // Set up the soft scan button click listener
        softScanButton.setOnClickListener {
            // Trigger a soft scan action
            triggerSoftScan(true)
        }

        // Initialize DataWedge profile on app startup to ensure correct configuration
        // This will now use the single SET_CONFIG method from the reference
        createAndConfigureDataWedgeProfile()
    }

    override fun onResume() {
        super.onResume()
        // Register broadcast receivers when the activity is active
        registerDataWedgeReceivers()
        // Register for scanner status notifications to keep the UI updated
        registerForScannerStatusNotifications(true)
    }

    override fun onPause() {
        super.onPause()
        // Unregister for scanner status notifications when the activity is paused
        registerForScannerStatusNotifications(false)
        // Unregister the broadcast receiver to prevent leaks
        try {
            unregisterReceiver(dataWedgeBroadcastReceiver)
        } catch (e: IllegalArgumentException) {
            Log.e("DataWedge", "Receiver was already unregistered onPause: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Ensure receiver is unregistered on activity destroy as well
        try {
            unregisterReceiver(dataWedgeBroadcastReceiver)
        } catch (e: IllegalArgumentException) {
            Log.e("DataWedge", "Receiver was already unregistered onDestroy: ${e.message}")
        }
    }


    /**
     * Registers broadcast receivers for DataWedge scanned data and status updates.
     * This method sets up the intent filters to listen for relevant broadcasts from DataWedge.
     *
     * IMPORTANT: For Android 12 (API 31) and higher, the RECEIVER_EXPORTED or RECEIVER_NOT_EXPORTED
     * flag MUST be specified when registering a dynamic BroadcastReceiver that is not exclusively
     * for system broadcasts. Since DataWedge is an external app, RECEIVER_EXPORTED is required.
     */
    private fun registerDataWedgeReceivers() {
        val filter = IntentFilter()
        filter.addCategory(Intent.CATEGORY_DEFAULT)
        // Action for receiving scanned barcode data. This action needs to be consistent
        // with the `intent_action` configured in the DataWedge profile.
        filter.addAction(resources.getString(R.string.activity_intent_filter_action))
        // Action for receiving scanner status notifications from DataWedge.
        filter.addAction(DataWedgeConstantsOld.ACTION_RESULT_NOTIFICATION)
        // Action for receiving results of DataWedge API calls (e.g., SET_CONFIG success/failure)
        filter.addAction(DataWedgeConstantsOld.ACTION_RESULT)


        // Check Android SDK version to apply the correct flag
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) { // Android 12 (API 31) is S
            ContextCompat.registerReceiver(
                this, // Context
                dataWedgeBroadcastReceiver, // BroadcastReceiver instance
                filter, // IntentFilter
                ContextCompat.RECEIVER_EXPORTED // Flag indicating receiver can receive broadcasts from other apps
            )
            Log.d("DataWedge", "Registered receiver with RECEIVER_EXPORTED flag for Android S+.")
        } else {
            // For older Android versions, use the standard registerReceiver method
            registerReceiver(dataWedgeBroadcastReceiver, filter)
            Log.d("DataWedge", "Registered receiver using standard method for Android < S.")
        }
    }

    /**
     * Configures the DataWedge profile for the application, creating it if it doesn't exist.
     * This method combines profile creation and plugin configuration into a single SET_CONFIG call
     * as demonstrated in the provided reference.
     */
    private fun createAndConfigureDataWedgeProfile() {
        Log.d("DataWedge", "Configuring DataWedge profile '${DataWedgeConstantsOld.PROFILE_NAME}'...")

        val bMain = Bundle() // Main bundle for SET_CONFIG payload
        val bConfig = Bundle() // Bundle for PLUGIN_CONFIG
        val bParams = Bundle() // Bundle for Intent plugin parameters

        // 1. Configure Intent Output Plugin Parameters
        bParams.putString("intent_output_enabled", "true")
        // Use YOUR APP'S intent action for data delivery
        bParams.putString("intent_action", resources.getString(R.string.activity_intent_filter_action))
        // Explicitly set the category for the intent. This is crucial for matching your receiver.
        bParams.putString("intent_category", Intent.CATEGORY_DEFAULT)
        // Set delivery method as Integer (2 for Broadcast) as per reference
        bParams.putInt("intent_delivery", 2) // 0 for Start Activity, 1 for Start Service, 2 for Broadcast

        // 2. Configure Intent Plugin itself
        bConfig.putString("PLUGIN_NAME", "INTENT")
        bConfig.putString("RESET_CONFIG", "false") // Set to false as per reference
        bConfig.putBundle("PARAM_LIST", bParams) // Add parameters to the plugin config

        // 3. Configure the main Profile
        bMain.putString("PROFILE_NAME", DataWedgeConstantsOld.PROFILE_NAME)
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
        i.action = DataWedgeConstantsOld.ACTION_DATAWEDGE_API // Use the general API action
        i.putExtra(DataWedgeConstantsOld.EXTRA_SET_CONFIG, bMain) // Pass the full profile configuration bundle
        i.putExtra(DataWedgeConstantsOld.EXTRA_COMMAND_IDENTIFIER, "SET_CONFIG_GoStockProfile") // Unique ID for this command
        i.putExtra(DataWedgeConstantsOld.EXTRA_SEND_RESULT, DataWedgeConstantsOld.SEND_RESULT_LAST_RESULT) // Use "LAST_RESULT" for robust result feedback

        applicationContext.sendBroadcast(i)
        Log.d("DataWedge", "Sent SET_CONFIG intent for profile: ${DataWedgeConstantsOld.PROFILE_NAME}")
    }

    /**
     * Sends an intent to DataWedge to trigger a soft scan.
     * This can simulate a hardware trigger or provide an in-app scan button.
     * @param enable If true, initiates a scan. If false, stops an ongoing scan.
     */
    private fun triggerSoftScan(enable: Boolean) {
        val i = Intent()
        i.action = DataWedgeConstantsOld.ACTION_DATAWEDGE_API // Use the general API action
        i.putExtra(DataWedgeConstantsOld.EXTRA_SOFT_SCAN_TRIGGER, if (enable) "START_SCANNING" else "STOP_SCANNING") // Correct extra key
        applicationContext.sendBroadcast(i)
        Log.d("DataWedge", "Soft scan trigger sent: ${if (enable) "START_SCANNING" else "STOP_SCANNING"}")
    }

    /**
     * Registers or unregisters for scanner status notifications from DataWedge.
     * This allows your app to receive updates on the scanner's state (e.g., IDLE, SCANNING, WAITING).
     * @param register True to register for notifications, false to unregister.
     */
    private fun registerForScannerStatusNotifications(register: Boolean) {
        val i = Intent()
        // Use the appropriate action for registering/unregistering notifications
        i.action = if (register) DataWedgeConstantsOld.ACTION_REGISTER_FOR_NOTIFICATION else DataWedgeConstantsOld.ACTION_UNREGISTER_FOR_NOTIFICATION

        val bundle = Bundle()
        // Key for the type of notification inside the NOTIFICATION_BUNDLE
        bundle.putString(DataWedgeConstantsOld.EXTRA_NOTIFICATION_TYPE_KEY_IN_BUNDLE, "SCANNER_STATUS")
        // The top-level extra which contains the notification bundle
        i.putExtra(DataWedgeConstantsOld.EXTRA_NOTIFICATION_BUNDLE, bundle)

        applicationContext.sendBroadcast(i)
        Log.d("DataWedge", "${if (register) "Registering" else "Unregistering"} for scanner status notifications.")
    }
}
