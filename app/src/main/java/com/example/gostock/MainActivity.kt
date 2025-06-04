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

class MainActivity : AppCompatActivity() {

    // Toolbar buttons (Back and Save)
    private lateinit var btnToolbarSave: ImageButton
    private lateinit var btnToolbarBack: ImageButton

    // Location Card elements
    private lateinit var cardLocation: MaterialCardView
    private lateinit var tvLocationValue: TextView
    // private lateinit var ivLocationBarcodeIcon: ImageView // Not directly used in latest XML
    private lateinit var ivLocationCheckIcon: ImageView

    // SKU Card elements
    private lateinit var cardSku: MaterialCardView
    private lateinit var tvSkuValue: TextView
    // private lateinit var ivSkuBarcodeIcon: ImageView // Not directly used in latest XML
    private lateinit var ivSkuCheckIcon: ImageView

    // Quantity Card elements
    private lateinit var cardQuantity: MaterialCardView
    private lateinit var etQuantity: EditText

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
                    tvLocationValue.text = it
                    ivLocationCheckIcon.visibility = View.VISIBLE
                    cardSku.isEnabled = true // ENABLE SKU card after successful location scan
                } else if (currentScanType == ScanType.SKU) {
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
            Toast.makeText(this, "Barcode scan cancelled or failed.", Toast.LENGTH_SHORT).show()
            updateSaveButtonState()
        }
        currentScanType = ScanType.NONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)



        btnToolbarSave = findViewById(R.id.btn_toolbar_save)
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)

        // Initialize Location Card elements
        cardLocation = findViewById(R.id.card_location)
        tvLocationValue = findViewById(R.id.tv_location_value)
        ivLocationCheckIcon = findViewById(R.id.iv_location_check_icon)
        // Initialize SKU Card elements
        cardSku = findViewById(R.id.card_sku)
        tvSkuValue = findViewById(R.id.tv_sku_value)
        ivSkuCheckIcon = findViewById(R.id.iv_sku_check_icon)

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
            performLogout()
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

        // --- CardView and ImageButton click listeners for scan actions ---
        // Location Card click (triggers scan)
        cardLocation.setOnClickListener {
            currentScanType = ScanType.LOCATION
            val intent = Intent(this, BarcodeScannerActivity::class.java)
            barcodeScannerLauncher.launch(intent)
        }


        // SKU Card click (triggers scan - Initially disabled, enabled after Location Scan)
        cardSku.setOnClickListener {
            currentScanType = ScanType.SKU
            val intent = Intent(this, BarcodeScannerActivity::class.java)
            barcodeScannerLauncher.launch(intent)
        }

        // --- END CardView and ImageButton click listeners ---

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
    }

    /**
     * Resets the UI fields and state for a new entry.
     */
    private fun resetInputFields() {
        tvLocationValue.text = "" // Reset text to empty
        ivLocationCheckIcon.visibility = View.GONE // Hide check icon
        tvSkuValue.text = "" // Reset text to empty
        ivSkuCheckIcon.visibility = View.GONE // Hide check icon
        etQuantity.text.clear() // Clear quantity
        cardSku.isEnabled = false // DISABLE SKU card initially
        updateSaveButtonState() // Update save button state (which will disable Save button if fields are empty)
    }

    /**
     * Updates the enabled state of the Save button based on input validity.
     */
    private fun updateSaveButtonState() {
        val isLocationScanned = tvLocationValue.text.toString().isNotEmpty()
        val isSkuScanned = tvSkuValue.text.toString().isNotEmpty()
        val isQuantityEntered = etQuantity.text.isNotBlank() && etQuantity.text.toString().toIntOrNull() != null && etQuantity.text.toString().toIntOrNull()!! > 0

        val canSave = isLocationScanned && isSkuScanned && isQuantityEntered
        btnToolbarSave.isEnabled = canSave // Control the toolbar save button
        btnToolbarBack.isEnabled = true // Back button should always be enabled
    }
}