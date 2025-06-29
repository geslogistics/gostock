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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.card.MaterialCardView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import android.os.Build
import android.util.Log
import com.example.gostock.DataWedgeConstants


import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

// MainActivity now implements ZebraScanResultListener to receive callbacks from ZebraScannerHelper
class MainActivity : AppCompatActivity(), ZebraScanResultListener {

    private lateinit var tvRecentEntriesTitle: TextView
    private lateinit var rvRecentEntries: RecyclerView
    private lateinit var tvNoRecentEntries: TextView
    private lateinit var recentEntryAdapter: RecentEntryAdapter

    // Toolbar buttons (Back and Save)
    private lateinit var btnToolbarSave: ImageButton
    private lateinit var btnToolbarClear: ImageButton
    private lateinit var btnToolbarBack: ImageButton

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

    private val TAG = "MainActivity"


    // Activity Result Launcher for Barcode Scanner (Camera-based)
    private val barcodeScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scannedBarcode = result.data?.getStringExtra(BarcodeScannerActivity.EXTRA_BARCODE_RESULT)
            processCameraScanResult(scannedBarcode) // Call helper function for camera results
        } else {
            // If scan was cancelled/failed, revert state for current scan type
            if (currentScanType == ScanType.LOCATION) {
                tvLocationValue.text = ""
                ivLocationCheckIcon.visibility = View.GONE
                cardSku.isEnabled = false // Re-disable SKU card
            } else if (currentScanType == ScanType.SKU) {
                tvSkuValue.text = ""
                ivSkuCheckIcon.visibility = View.GONE
            }
            Toast.makeText(this, "Barcode scan cancelled or failed.", Toast.LENGTH_SHORT).show()
            updateSaveButtonState()
        }
        currentScanType = ScanType.NONE // Reset after processing scan
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize UI elements
        btnToolbarSave = findViewById(R.id.btn_toolbar_save)
        btnToolbarClear = findViewById(R.id.btn_toolbar_clear)
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)

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
            Toast.makeText(this, "User not logged in. Redirecting to login.", Toast.LENGTH_LONG).show()
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
        // This is done on app start (or main activity launch) regardless of which activity is primary
        if (isZebraDevice && AppSettings.enableZebraDevice) {
            zebraScannerHelper.setupDataWedgeProfile()
            Toast.makeText(this, "Zebra DataWedge scanner enabled.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Using camera for scanning.", Toast.LENGTH_SHORT).show()
        }


        setupRecentEntriesRecyclerView()
        setupClickListeners()
        resetInputFields() // Call this initially to set up states and empty text
        updateSaveButtonState() // Initial check for save button enablement
    }

    override fun onResume() {
        super.onResume()
        // Register receiver and activate profile if Zebra is enabled
        if (isZebraDevice && AppSettings.enableZebraDevice) {
            zebraScannerHelper.registerReceiver() // Register receiver on resume
            zebraScannerHelper.activateProfile(DataWedgeConstants.PROFILE_NAME)
            zebraScannerHelper.enableBarcodePlugin() // Ensure barcode scanner is enabled
        }
        loadRecentEntries() // Reload recent entries on resume
    }

    override fun onPause() {
        super.onPause()
        // Unregister receiver and disable plugin on pause for Zebra devices
        if (isZebraDevice && AppSettings.enableZebraDevice) {
            zebraScannerHelper.stopSoftScan()
            zebraScannerHelper.disableBarcodePlugin()
            zebraScannerHelper.unregisterReceiver() // Unregister receiver on pause
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up DataWedge profile on destroy (final cleanup)
        if (isZebraDevice && AppSettings.enableZebraDevice) {
            zebraScannerHelper.activateProfile("") // Deactivate our profile
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
        Toast.makeText(this, "Logged out successfully!", Toast.LENGTH_SHORT).show()
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
            resetInputFields() // Call the existing reset function
            Toast.makeText(this, "Fields cleared.", Toast.LENGTH_SHORT).show()
        }

        btnToolbarSave.setOnClickListener {
            saveStockEntry(resetFields = true)
        }



        // --- Scan Triggers (Conditional for Zebra vs Camera) ---

        // Location Card/Button click
        cardLocation.setOnClickListener {
            if (isZebraDevice && AppSettings.enableZebraDevice) {
                // In seamless Zebra mode, card click does NOT initiate scan.
                // Scan is triggered by physical button press.
                // This click is now a prompt/focus indicator.
                Toast.makeText(this, "Scan Location with physical scanner.", Toast.LENGTH_SHORT).show()
                // Optionally, request soft scan if you also want a screen button to trigger it
                // zebraScannerHelper.startSoftScan()
            } else {
                // Camera mode: launch camera activity
                currentScanType = ScanType.LOCATION // Set scan type for camera activity result
                val intent = Intent(this, BarcodeScannerActivity::class.java)
                barcodeScannerLauncher.launch(intent)
                Toast.makeText(this, "Ready to scan location (Camera).", Toast.LENGTH_SHORT).show()
            }
        }

        // SKU Card/Button click
        cardSku.setOnClickListener {
            // Prevent scan if card is disabled (location not scanned yet)
            if (!cardSku.isEnabled) {
                Toast.makeText(this, "Please scan Location first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (isZebraDevice && AppSettings.enableZebraDevice) {
                // In seamless Zebra mode, card click does NOT initiate scan.
                // Scan is triggered by physical button press.
                // This click is now a prompt/focus indicator.
                Toast.makeText(this, "Scan SKU with physical scanner.", Toast.LENGTH_SHORT).show()
                // Optionally, request soft scan if you also want a screen button to trigger it
                // zebraScannerHelper.startSoftScan()
            } else {
                // Camera mode: launch camera activity
                currentScanType = ScanType.SKU // Set scan type for camera activity result
                val intent = Intent(this, BarcodeScannerActivity::class.java)
                barcodeScannerLauncher.launch(intent)
                Toast.makeText(this, "Ready to scan SKU (Camera).", Toast.LENGTH_SHORT).show()
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
        if (selectedUser.isEmpty() || location.isEmpty() || sku.isEmpty() || quantity == null || quantity <= 0) {
            Toast.makeText(this, "Please complete all fields correctly (Location, SKU, Quantity > 0)", Toast.LENGTH_LONG).show()
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
        Toast.makeText(this, "Entry Saved!", Toast.LENGTH_SHORT).show()

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
    private fun resetInputFields() { // Renamed from resetInputFields for now to avoid conflict
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
    override fun onZebraScanResult(scanData: String?) {
        // This method is called by ZebraScannerHelper when a scan result is received
        // This is the seamless scanning part
        processZebraScanResult(scanData)
    }

    override fun onZebraScanError(errorMessage: String) {
        // Handle scan errors, e.g., show a toast or log
        Toast.makeText(this, "Zebra Scan Error: $errorMessage", Toast.LENGTH_LONG).show()
        Log.e(TAG, "Zebra Scan Error: $errorMessage")
        // No need to reset currentScanType here, as the physical scanner might just try again
    }

    /** Processes the scan result received directly from DataWedge (from ZebraScannerHelper). */
    private fun processZebraScanResult(scanData: String?) {
        scanData?.let {
            // Determine scan destination based on current UI state, not currentScanType
            // This is the core logic for automatic progression
            if (tvLocationValue.text.toString().isEmpty()) { // Location is empty, fill location
                tvLocationValue.text = it
                ivLocationCheckIcon.visibility = View.VISIBLE
                cardSku.isEnabled = true
                // Do NOT set currentScanType here, it will be automatically handled by next scan
                Toast.makeText(this, "Location scanned: $it", Toast.LENGTH_SHORT).show()
                // Optionally immediately focus SKU and keyboard if you want to force next step
                // tvSkuValue.requestFocus()
                // val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                // imm.showSoftInput(tvSkuValue, InputMethodManager.SHOW_IMPLICIT)

            } else if (tvSkuValue.text.toString().isEmpty()) { // Location is filled, SKU is empty, fill SKU
                tvSkuValue.text = it
                ivSkuCheckIcon.visibility = View.VISIBLE
                etQuantity.requestFocus() // Focus quantity field after SKU scan
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                Handler(Looper.getMainLooper()).postDelayed({
                    imm.showSoftInput(etQuantity, InputMethodManager.SHOW_IMPLICIT)
                }, 200)
                Toast.makeText(this, "SKU scanned: $it", Toast.LENGTH_SHORT).show()
            } else {
                // Both Location and SKU are filled. Do not accept more scans until saved/cleared.
                Toast.makeText(this, "Location and SKU already scanned. Save or clear to continue.", Toast.LENGTH_LONG).show()
                Log.d(TAG, "Scan ignored: Both Location and SKU already filled.")
                return // Do not update state or process further
            }
            updateSaveButtonState()
        }
        // currentScanType remains what it was for camera, but for zebra, it's state-driven
        // No explicit currentScanType = ScanType.NONE here because we're reacting to physical scans.
    }

    /** Processes the scan result received from the Camera (BarcodeScannerActivity). */
    private fun processCameraScanResult(scanData: String?) {
        scanData?.let {
            if (currentScanType == ScanType.LOCATION) { // Still relies on currentScanType
                tvLocationValue.text = it
                ivLocationCheckIcon.visibility = View.VISIBLE
                cardSku.isEnabled = true
            } else if (currentScanType == ScanType.SKU) { // Still relies on currentScanType
                tvSkuValue.text = it
                ivSkuCheckIcon.visibility = View.VISIBLE
                etQuantity.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                Handler(Looper.getMainLooper()).postDelayed({
                    imm.showSoftInput(etQuantity, InputMethodManager.SHOW_IMPLICIT)
                }, 200)
            }
            updateSaveButtonState()
            Toast.makeText(this, "Barcode scanned: $it", Toast.LENGTH_SHORT).show()
        }
        currentScanType = ScanType.NONE // Reset after processing camera scan
    }
}