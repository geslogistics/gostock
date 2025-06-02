package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton // Make sure this is imported
import android.widget.ImageView // ADD THIS IMPORT
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.cardview.widget.CardView // ADD THIS IMPORT
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    // Toolbar buttons (Back and Save)
    private lateinit var btnToolbarSave: ImageButton
    private lateinit var btnToolbarBack: ImageButton

    // Location Card elements
    private lateinit var cardLocation: CardView
    private lateinit var tvLocationValue: TextView // The value displayed
    private lateinit var ivLocationBarcodeIcon: ImageView // Static barcode icon inside card
    private lateinit var btnLocationScanAction: ImageButton // Scan/Refresh button inside card

    // SKU Card elements
    private lateinit var cardSku: CardView
    private lateinit var tvSkuValue: TextView // The value displayed
    private lateinit var ivSkuBarcodeIcon: ImageView // Static barcode icon inside card
    private lateinit var btnSkuScanAction: ImageButton // Scan/Refresh button inside card

    // Quantity Card elements
    private lateinit var cardQuantity: CardView
    private lateinit var etQuantity: EditText // Input for quantity

    // Internal state
    private var selectedUser: String = "" // Captured from logged-in user
    private lateinit var fileHandler: FileHandler

    // Enum to track current scan type
    enum class ScanType {
        LOCATION, SKU, NONE
    }
    private var currentScanType: ScanType = ScanType.NONE

    // Activity Result Launcher for Barcode Scanner
    private val barcodeScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scannedBarcode = result.data?.getStringExtra(BarcodeScannerActivity.EXTRA_BARCODE_RESULT)
            scannedBarcode?.let {
                if (currentScanType == ScanType.LOCATION) {
                    tvLocationValue.text = it // Update Location card's TextView
                    btnLocationScanAction.setImageResource(R.drawable.ic_refresh_icon) // Change icon to refresh
                    // Enable SKU scan action after successful location scan
                    btnSkuScanAction.isEnabled = true
                } else if (currentScanType == ScanType.SKU) {
                    tvSkuValue.text = it // Update SKU card's TextView
                    btnSkuScanAction.setImageResource(R.drawable.ic_refresh_icon) // Change icon to refresh
                    etQuantity.requestFocus()
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    Handler(Looper.getMainLooper()).postDelayed({
                        imm.showSoftInput(etQuantity, InputMethodManager.SHOW_IMPLICIT)
                    }, 200)
                }
                updateSaveButtonState()
                Toast.makeText(this, "Barcode scanned: $it", Toast.LENGTH_SHORT).show()
            }
        } else {
            // If scan was cancelled/failed, and it was a location scan, keep SKU disabled
            if (currentScanType == ScanType.LOCATION) {
                // If location scan was cancelled, revert icon and disable SKU scan button
                btnLocationScanAction.setImageResource(R.drawable.ic_scan_qrcode_icon) // Revert to scan icon
                tvLocationValue.text = "" // Revert text
                btnSkuScanAction.isEnabled = false // Disable SKU scan action
            } else if (currentScanType == ScanType.SKU) {
                // If SKU scan was cancelled, revert icon and value if it was a new scan
                btnSkuScanAction.setImageResource(R.drawable.ic_scan_barcode_icon)
                tvSkuValue.text = ""
            }
            Toast.makeText(this, "Barcode scan cancelled or failed.", Toast.LENGTH_SHORT).show()
            updateSaveButtonState() // Re-evaluate save button state after cancel
        }
        currentScanType = ScanType.NONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Toolbar and its buttons
        val toolbar = findViewById<Toolbar>(R.id.toolbar_main)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // Hide default title for custom layout

        btnToolbarSave = findViewById(R.id.btn_toolbar_save)
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)

        // Initialize Location Card elements
        cardLocation = findViewById(R.id.card_location)
        tvLocationValue = findViewById(R.id.tv_location_value)
        btnLocationScanAction = findViewById(R.id.btn_location_scan_action)

        // Initialize SKU Card elements
        cardSku = findViewById(R.id.card_sku)
        tvSkuValue = findViewById(R.id.tv_sku_value)
        btnSkuScanAction = findViewById(R.id.btn_sku_scan_action)

        // Initialize Quantity Card element
        cardQuantity = findViewById(R.id.card_quantity)
        etQuantity = findViewById(R.id.et_quantity)


        // Initialize FileHandler
        fileHandler = FileHandler(this)

        // Set logged-in user for stock entries
        GoStockApp.loggedInUser?.let {
            selectedUser = it.username
        } ?: run {
            Toast.makeText(this, "User not logged in. Redirecting to login.", Toast.LENGTH_LONG).show()
            performLogout() // Force logout if no user is found
        }

        setupClickListeners()
        resetInputFields() // Call this initially to set up states and text like ""
        updateSaveButtonState() // Initial check for save button enablement
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
     * Sets up click listeners for all interactive UI elements.
     */
    private fun setupClickListeners() {
        // Toolbar Back button
        btnToolbarBack.setOnClickListener {
            finish() // Go back to HomeActivity
        }

        // Toolbar Save button
        btnToolbarSave.setOnClickListener {
            saveStockEntry(resetFields = true) // Save and clear fields, stay on page
        }

        // Location Scan action button
        btnLocationScanAction.setOnClickListener {
            currentScanType = ScanType.LOCATION
            val intent = Intent(this, BarcodeScannerActivity::class.java)
            barcodeScannerLauncher.launch(intent)
        }

        // SKU Scan action button
        btnSkuScanAction.setOnClickListener {
            currentScanType = ScanType.SKU
            val intent = Intent(this, BarcodeScannerActivity::class.java)
            barcodeScannerLauncher.launch(intent)
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
        // Username is automatically the logged-in user
        val location = tvLocationValue.text.toString() // Get value from new TextView
        val sku = tvSkuValue.text.toString() // Get value from new TextView
        val quantity = etQuantity.text.toString().toIntOrNull()

        // Validation based on new placeholder text
        if (selectedUser.isEmpty() || location == "" || sku == "" || quantity == null || quantity <= 0) {
            Toast.makeText(this, "Please complete all fields correctly (Location, SKU, Quantity > 0)", Toast.LENGTH_LONG).show()
            return // Stop the function if validation fails
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
        }
    }

    /**
     * Resets the UI fields and state for a new entry.
     */
    private fun resetInputFields() {
        tvLocationValue.text = "" // Reset text
        btnLocationScanAction.setImageResource(R.drawable.ic_scan_qrcode_icon) // Reset icon to scan
        tvSkuValue.text = "" // Reset text
        btnSkuScanAction.setImageResource(R.drawable.ic_scan_barcode_icon) // Reset icon to scan
        etQuantity.text.clear() // Clear quantity
        btnSkuScanAction.isEnabled = false // Disable SKU scan initially
        updateSaveButtonState() // Update save button state (which will disable Save button if fields are N/A)
    }

    /**
     * Updates the enabled state of the Save button based on input validity.
     */
    private fun updateSaveButtonState() {
        val isLocationScanned = tvLocationValue.text.toString() != ""
        val isSkuScanned = tvSkuValue.text.toString() != ""
        val isQuantityEntered = etQuantity.text.isNotBlank() && etQuantity.text.toString().toIntOrNull() != null && etQuantity.text.toString().toIntOrNull()!! > 0

        val canSave = isLocationScanned && isSkuScanned && isQuantityEntered
        btnToolbarSave.isEnabled = canSave // Control the toolbar save button
        btnToolbarBack.isEnabled = true // Back button should always be enabled
    }
}