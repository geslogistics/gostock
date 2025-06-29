package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.card.MaterialCardView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible // Ensure this is needed, if not, remove
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.os.Build
import android.util.Log // ADDED for logging
import com.example.gostock.DataWedgeConstants


import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// MainActivity now implements ZebraScanResultListener to receive callbacks from ZebraScannerHelper
class MainActivity : AppCompatActivity(), ZebraScanResultListener {

    private fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_LONG) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, duration)

        // Make Snackbar text multiline (works up to a certain point before system truncates)
        val snackbarTextView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        snackbarTextView.maxLines = 5 // Allow up to 5 lines (adjust as needed)
        snackbarTextView.ellipsize = null // Remove ellipsis if text exceeds maxLines

        snackbar.show()
    }

    private lateinit var tvRecentEntriesTitle: TextView
    private lateinit var rvRecentEntries: RecyclerView
    private lateinit var tvNoRecentEntries: TextView
    private lateinit var recentEntryAdapter: RecentEntryAdapter

    // Toolbar buttons (Back and Save)
    private lateinit var btnToolbarSave: ImageButton
    private lateinit var btnToolbarBack: ImageButton
    private lateinit var btnToolbarClear: ImageButton

    // Location Card elements
    private lateinit var cardLocation: MaterialCardView
    private lateinit var tvLocationValue: TextView
    private lateinit var ivLocationCheckIcon: ImageView

    // SKU Card elements
    private lateinit var cardSku: MaterialCardView
    private lateinit var tvSkuValue: TextView
    private lateinit var ivSkuCheckIcon: ImageView

    // Quantity Card elements
    private lateinit var cardQuantity: MaterialCardView
    private lateinit var etQuantity: EditText

    // Internal state
    private var selectedUser: String = ""
    private lateinit var fileHandler: FileHandler

    // Enum to track current scan type
    enum class ScanType {
        LOCATION, SKU, NONE // NONE is important when not actively expecting a specific scan
    }
    private var currentScanType: ScanType = ScanType.NONE // Default to NONE

    // Zebra Scanner related
    private var isZebraDevice = false
    private lateinit var zebraScannerHelper: ZebraScannerHelper

    private val TAG = "MainActivity" // Tag for logging for MainActivity itself


    // Activity Result Launcher for Barcode Scanner (Camera-based)
    private val barcodeScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scannedBarcode = result.data?.getStringExtra(BarcodeScannerActivity.EXTRA_BARCODE_RESULT)
            val scannedSymbology = result.data?.getStringExtra(BarcodeScannerActivity.EXTRA_SYMBOLOGY_TYPE) // Get symbology from Camera scan
            processCameraScanResult(scannedBarcode, scannedSymbology) // Pass symbology
        } else {
            // If scan was cancelled/failed
            if (currentScanType == ScanType.LOCATION) {
                tvLocationValue.text = ""
                ivLocationCheckIcon.visibility = View.GONE
                cardSku.isEnabled = false
            } else if (currentScanType == ScanType.SKU) {
                tvSkuValue.text = ""
                ivSkuCheckIcon.visibility = View.GONE
            }
            showSnackbar( "Barcode scan cancelled or failed.", Snackbar.LENGTH_SHORT)
            updateSaveButtonState()
        }
        currentScanType = ScanType.NONE // Reset after processing scan
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        btnToolbarSave = findViewById(R.id.btn_toolbar_save)
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)
        btnToolbarClear = findViewById(R.id.btn_toolbar_clear) // Initialize the clear button


        cardLocation = findViewById(R.id.card_location)
        tvLocationValue = findViewById(R.id.tv_location_value)
        ivLocationCheckIcon = findViewById(R.id.iv_location_check_icon)

        cardSku = findViewById(R.id.card_sku)
        tvSkuValue = findViewById(R.id.tv_sku_value)
        ivSkuCheckIcon = findViewById(R.id.iv_sku_check_icon)

        cardQuantity = findViewById(R.id.card_quantity)
        etQuantity = findViewById(R.id.et_quantity)

        tvRecentEntriesTitle = findViewById(R.id.tv_recent_entries_title)
        rvRecentEntries = findViewById(R.id.rv_recent_entries)
        tvNoRecentEntries = findViewById(R.id.tv_no_recent_entries)


        // Initialize FileHandler
        fileHandler = FileHandler(this)

        // Set logged-in user for stock entries
        GoStockApp.loggedInUser?.let {
            selectedUser = it.username
        } ?: run {
            showSnackbar( "User not logged in. Redirecting to login.", Snackbar.LENGTH_LONG)
            performLogout()
        }

        // Determine if this is a Zebra device
        isZebraDevice = Build.MANUFACTURER.equals("Zebra Technologies", ignoreCase = true) ||
                Build.MODEL.startsWith("TC", ignoreCase = true) ||
                Build.MODEL.startsWith("MC", ignoreCase = true) ||
                Build.MODEL.startsWith("ET", ignoreCase = true)
        Log.d(TAG, "Device Manufacturer: ${Build.MANUFACTURER}, Model: ${Build.MODEL}, Is Zebra Device: $isZebraDevice")

        // Initialize ZebraScannerHelper. 'this' refers to MainActivity, which implements ZebraScanResultListener
        zebraScannerHelper = ZebraScannerHelper(this, this)

        // Setup DataWedge profile and register receiver if it's a Zebra device AND setting is enabled
        if (isZebraDevice && AppSettings.enableZebraDevice) {
            zebraScannerHelper.setupDataWedgeProfile() // Setup profile
            zebraScannerHelper.registerReceiver() // Register the broadcast receiver
            showSnackbar( "Zebra DataWedge scanner enabled.", Snackbar.LENGTH_SHORT)
        } else {
            showSnackbar( "Using camera for scanning.", Snackbar.LENGTH_SHORT)
        }


        setupRecentEntriesRecyclerView()
        setupClickListeners()
        resetInputFields() // Call this initially to set up states and empty text
        updateSaveButtonState() // Initial check for save button enablement
    }

    override fun onResume() {
        super.onResume()
        // If it's a Zebra device and setting is enabled, ensure DataWedge profile is active and scanner is ready
        if (isZebraDevice && AppSettings.enableZebraDevice) {
            zebraScannerHelper.activateProfile(DataWedgeConstants.PROFILE_NAME) // Activate our profile using DataWedgeConstants
            zebraScannerHelper.enableBarcodePlugin() // Ensure barcode scanner is enabled
        }
        loadRecentEntries() // Reload recent entries on resume
    }

    override fun onPause() {
        super.onPause()
        // If it's a Zebra device and setting is enabled, stop active scans and disable plugin on pause
        if (isZebraDevice && AppSettings.enableZebraDevice) {
            zebraScannerHelper.stopSoftScan() // Stop any active scan
            zebraScannerHelper.disableBarcodePlugin() // Disable barcode scanner plugin
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister DataWedge receiver and deactivate profile if it was enabled
        if (isZebraDevice && AppSettings.enableZebraDevice) {
            try {
                zebraScannerHelper.unregisterReceiver() // Unregister the broadcast receiver
            } catch (e: Exception) {
                // Handle cases where receiver might already be unregistered
                Log.e(TAG, "Error unregistering DataWedge receiver: ${e.message}")
            }
            zebraScannerHelper.activateProfile("") // Deactivate our profile to clean up
        }
    }

    private fun setupRecentEntriesRecyclerView() {
        recentEntryAdapter = RecentEntryAdapter(emptyList())
        rvRecentEntries.layoutManager = LinearLayoutManager(this)
        rvRecentEntries.adapter = recentEntryAdapter
    }

    private fun loadRecentEntries() {
        val allEntries = fileHandler.loadStockEntries()
        val recentEntries = allEntries.sortedByDescending { it.timestamp }.take(3)

        if (recentEntries.isNotEmpty()) {
            recentEntryAdapter.updateData(recentEntries)
            tvRecentEntriesTitle.visibility = View.VISIBLE
            rvRecentEntries.visibility = View.VISIBLE
            tvNoRecentEntries.visibility = View.GONE
        } else {
            tvRecentEntriesTitle.visibility = View.GONE
            rvRecentEntries.visibility = View.GONE
            tvNoRecentEntries.visibility = View.VISIBLE
        }
    }

    private fun performLogout() {
        (application as GoStockApp).clearLoginSession()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        showSnackbar( "Logged out successfully!", Snackbar.LENGTH_SHORT)
    }


    /**
     * Sets up click listeners for UI elements.
     * Logic here is modified for seamless Zebra scanning experience.
     */
    private fun setupClickListeners() {
        btnToolbarBack.setOnClickListener {
            finish()
        }

        btnToolbarClear.setOnClickListener {
            resetInputFields()
            showSnackbar( "Fields cleared.", Snackbar.LENGTH_SHORT)
        }

        btnToolbarSave.setOnClickListener {
            saveStockEntry(resetFields = true)
        }

        // Location Card click (triggers scan)
        cardLocation.setOnClickListener {
            // In Zebra mode, card click does NOT initiate scan directly from here.
            // Scan is triggered by physical button press, and result comes via BroadcastReceiver.
            // This click is now an optional prompt/focus indicator.
            if (isZebraDevice && AppSettings.enableZebraDevice) {
                showSnackbar( "Scan Location with physical scanner.", Snackbar.LENGTH_SHORT)
                // Optionally: request soft scan if you also want a screen button to trigger it
                // zebraScannerHelper.startSoftScan()
                currentScanType = ScanType.LOCATION // Set context for incoming Zebra scan
            } else {
                // Camera mode: launch camera activity
                currentScanType = ScanType.LOCATION // Set scan type for camera activity result
                val intent = Intent(this, BarcodeScannerActivity::class.java)
                barcodeScannerLauncher.launch(intent)
                showSnackbar( "Ready to scan location (Camera).", Snackbar.LENGTH_SHORT)
            }
        }

        // SKU Card click (triggers scan - Initially disabled, enabled after Location Scan)
        cardSku.setOnClickListener {
            // Prevent scan if card is disabled (location not scanned yet)
            if (!cardSku.isEnabled) {
                showSnackbar( "Please scan Location first.", Snackbar.LENGTH_SHORT)
                return@setOnClickListener
            }

            // In Zebra mode, card click does NOT initiate scan directly from here.
            // Scan is triggered by physical button press, and result comes via BroadcastReceiver.
            // This click is now an optional prompt/focus indicator.
            if (isZebraDevice && AppSettings.enableZebraDevice) {
                showSnackbar( "Scan SKU with physical scanner.", Snackbar.LENGTH_SHORT)
                // Optionally: request soft scan if you also want a screen button to trigger it
                // zebraScannerHelper.startSoftScan()
                currentScanType = ScanType.SKU // Set context for incoming Zebra scan
            } else {
                // Camera mode: launch camera activity
                currentScanType = ScanType.SKU // Set scan type for camera activity result
                val intent = Intent(this, BarcodeScannerActivity::class.java)
                barcodeScannerLauncher.launch(intent)
                showSnackbar( "Ready to scan SKU (Camera).", Snackbar.LENGTH_SHORT)
            }
        }

        // TextWatcher for quantity to enable/disable save button
        etQuantity.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSaveButtonState()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    /**
     * Saves the current stock entry data.
     * @param resetFields If true, clears the input fields after saving.
     */
    private fun saveStockEntry(resetFields: Boolean) {
        val location = tvLocationValue.text.toString()
        val sku = tvSkuValue.text.toString()
        val quantity = etQuantity.text.toString().toIntOrNull()

        // Validation based on empty text
        // Note: selectedUser is guaranteed to be non-empty due to GoStockApp.loggedInUser check
        if (location.isEmpty() || sku.isEmpty() || quantity == null || quantity <= 0) {
            showSnackbar( "Please complete all fields correctly (Location, SKU, Quantity > 0)", Snackbar.LENGTH_LONG)
            return
        }

        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val newEntry = StockEntry(
            timestamp = timestamp,
            username = selectedUser,
            locationBarcode = location,
            skuBarcode = sku,
            quantity = quantity
        )

        fileHandler.addStockEntry(newEntry)
        showSnackbar( "Entry Saved!", Snackbar.LENGTH_SHORT)

        if (resetFields) {
            resetInputFields()
            etQuantity.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etQuantity.windowToken, 0)
        }
        loadRecentEntries()
    }

    /**
     * Resets the UI fields and state for a new entry.
     */
    private fun resetInputFields() {
        tvLocationValue.text = ""
        ivLocationCheckIcon.visibility = View.GONE
        tvSkuValue.text = ""
        ivSkuCheckIcon.visibility = View.GONE
        etQuantity.text.clear()
        cardSku.isEnabled = false
        updateSaveButtonState()
    }

    /**
     * Updates the enabled state of the Save button based on input validity.
     */
    private fun updateSaveButtonState() {
        val isLocationScanned = tvLocationValue.text.toString().isNotEmpty()
        val isSkuScanned = tvSkuValue.text.toString().isNotEmpty()
        val isQuantityEntered = etQuantity.text.isNotBlank() && etQuantity.text.toString().toIntOrNull() != null && etQuantity.text.toString().toIntOrNull()!! > 0

        val canSave = isLocationScanned && isSkuScanned && isQuantityEntered
        btnToolbarSave.isEnabled = canSave
        btnToolbarBack.isEnabled = true
        btnToolbarClear.isEnabled = true
    }

    // --- ZebraScanResultListener Implementation ---
    override fun onZebraScanResult(scanData: String?, symbology: String?) { // MODIFIED: Added symbology
        // This method is called by ZebraScannerHelper when a scan result is received
        // This is the seamless scanning part
        processZebraScanResult(scanData, symbology)
    }

    override fun onZebraScanError(errorMessage: String) {
        // Handle scan errors, e.g., show a toast or log
        showSnackbar( "Zebra Scan Error: $errorMessage", Snackbar.LENGTH_LONG)
        Log.e(TAG, "Zebra Scan Error: $errorMessage")
        // No need to reset currentScanType here, as the physical scanner might just try again
    }

    /** Processes the scan result received directly from DataWedge (from ZebraScannerHelper). */
    private fun processZebraScanResult(scanData: String?, scannedSymbology: String?) { // ADDED scannedSymbology
        scanData?.let {
            val acceptedFormats: Set<String>
            val scanPurpose: String

            // Determine scan destination based on current UI state
            if (tvLocationValue.text.toString().isEmpty()) { // Location is empty, fill location
                acceptedFormats = AppSettings.acceptedLocationFormats
                scanPurpose = "Location"
            } else if (tvSkuValue.text.toString().isEmpty()) { // Location is filled, SKU is empty, fill SKU
                acceptedFormats = AppSettings.acceptedSkuFormats
                scanPurpose = "SKU"
            } else {
                showSnackbar( "Location and SKU already scanned. Save or clear to continue.", Snackbar.LENGTH_LONG)
                Log.d(TAG, "Scan ignored: Both Location and SKU already filled.")
                return // Do not update state or process further
            }

            // Validate scanned format
            if (!AppSettings.isFormatAccepted(scannedSymbology, acceptedFormats)) {
                val acceptedFormatsString = if (acceptedFormats.isEmpty()) "any format" else acceptedFormats.joinToString(", ")
                showSnackbar("Scanned $scanPurpose barcode format ($scannedSymbology) is not accepted. Only $acceptedFormatsString barcodes are accepted for $scanPurpose.", Snackbar.LENGTH_LONG)




                Log.w(TAG, "Zebra scan rejected: Format '$scannedSymbology' not accepted for $scanPurpose.")
                return // Reject scan if format is not accepted
            }

            // Apply valid scan result to appropriate field
            if (scanPurpose == "Location") {
                tvLocationValue.text = it
                ivLocationCheckIcon.visibility = View.VISIBLE
                cardSku.isEnabled = true
                showSnackbar( "Location scanned: $it", Snackbar.LENGTH_SHORT)
            } else if (scanPurpose == "SKU") {
                tvSkuValue.text = it
                ivSkuCheckIcon.visibility = View.VISIBLE
                etQuantity.requestFocus() // Focus quantity field after SKU scan
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                Handler(Looper.getMainLooper()).postDelayed({
                    imm.showSoftInput(etQuantity, InputMethodManager.SHOW_IMPLICIT)
                }, 200)
                showSnackbar( "SKU scanned: $it", Snackbar.LENGTH_SHORT)
            }
            updateSaveButtonState() // Update save button state after successful scan
        }
    }

    /** Processes the scan result received from the Camera (BarcodeScannerActivity). */
    private fun processCameraScanResult(scanData: String?, scannedSymbology: String?) { // ADDED scannedSymbology
        scanData?.let {
            val acceptedFormats: Set<String>
            val scanPurpose: String

            // Determine scan purpose based on currentScanType (set before launching camera)
            if (currentScanType == ScanType.LOCATION) {
                acceptedFormats = AppSettings.acceptedLocationFormats
                scanPurpose = "Location"
            } else if (currentScanType == ScanType.SKU) {
                acceptedFormats = AppSettings.acceptedSkuFormats
                scanPurpose = "SKU"
            } else {
                showSnackbar( "Unknown scan type. Scan ignored.", Snackbar.LENGTH_LONG)
                Log.w(TAG, "Camera scan ignored: Unknown currentScanType.")
                return
            }

            // Validate scanned format
            if (!AppSettings.isFormatAccepted(scannedSymbology, acceptedFormats)) {
                val acceptedFormatsString = if (acceptedFormats.isEmpty()) "any format" else acceptedFormats.joinToString(", ")
                showSnackbar("Scanned $scanPurpose barcode format ($scannedSymbology) is not accepted. Only $acceptedFormatsString barcodes are accepted for $scanPurpose.", Snackbar.LENGTH_LONG)
                Log.w(TAG, "Camera scan rejected: Format '$scannedSymbology' not accepted for $scanPurpose.")
                return // Reject scan if format is not accepted
            }

            // Apply valid scan result to appropriate field
            if (scanPurpose == "Location") {
                tvLocationValue.text = it
                ivLocationCheckIcon.visibility = View.VISIBLE
                cardSku.isEnabled = true
            } else if (scanPurpose == "SKU") {
                tvSkuValue.text = it
                ivSkuCheckIcon.visibility = View.VISIBLE
                etQuantity.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                Handler(Looper.getMainLooper()).postDelayed({
                    imm.showSoftInput(etQuantity, InputMethodManager.SHOW_IMPLICIT)
                }, 200)
            }
            updateSaveButtonState()
            showSnackbar("Barcode scanned: $it", Snackbar.LENGTH_LONG)
        }
        currentScanType = ScanType.NONE // Reset after processing camera scan
    }
}