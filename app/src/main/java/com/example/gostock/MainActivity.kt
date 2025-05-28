package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

import android.view.Gravity
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.Toolbar
import android.view.inputmethod.InputMethodManager // ADD THIS IMPORT
import android.content.Context // Ensure this is also imported, usually it is
import android.os.Handler // ADD THIS IMPORT
import android.os.Looper // ADD THIS IMPORT (for Handler(Looper.getMainLooper()))


class MainActivity : AppCompatActivity() {

    // Declare UI elements as properties
    private lateinit var btnScanLocation: Button
    private lateinit var tvLocationBarcode: TextView
    private lateinit var btnScanSku: Button
    private lateinit var tvSkuBarcode: TextView
    private lateinit var etQuantity: EditText
    private lateinit var btnSaveNew: Button // Now functions as "Save" (and clears)
    private lateinit var btnCancel: Button   // New name for "Cancel"

    // Toolbar and Breadcrumb elements
    private lateinit var toolbar: Toolbar
    private lateinit var tvLoggedInUserMain: TextView // For displaying user in toolbar
    private lateinit var tvBreadcrumbHome: TextView

    // To hold the currently logged-in username (automatically captured)
    private var selectedUser: String = ""

    // FileHandler instance
    private lateinit var fileHandler: FileHandler

    // Enum to keep track of what we are currently scanning
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
                    tvLocationBarcode.text = it // Directly set the scanned value, no prefix
                    btnScanSku.isEnabled = true // ENABLE SCAN SKU button after successful location scan
                } else if (currentScanType == ScanType.SKU) {
                    tvSkuBarcode.text = it // Directly set the scanned value, no prefix

                    // --- MODIFIED CODE FOR AUTO-FOCUS AND KEYBOARD ---
                    etQuantity.requestFocus() // Request focus on the quantity field

                    // Post a runnable to open the keyboard after a short delay
                    Handler(Looper.getMainLooper()).postDelayed({
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(etQuantity, InputMethodManager.SHOW_IMPLICIT)
                    }, 200) // 200 milliseconds delay, you can adjust this if needed
                    // --------------------------------------------------------


                }
                updateSaveButtonState()
                Toast.makeText(this, "Barcode scanned: $it", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Barcode scan cancelled or failed.", Toast.LENGTH_SHORT).show()
        }
        currentScanType = ScanType.NONE // Reset scan type after result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize Toolbar
        toolbar = findViewById(R.id.toolbar_main)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false) // Hide default title

        tvLoggedInUserMain = findViewById(R.id.tv_logged_in_user_main)
        tvBreadcrumbHome = findViewById(R.id.tv_breadcrumb_home)

        // Initialize UI elements
        btnScanLocation = findViewById(R.id.btn_scan_location)
        tvLocationBarcode = findViewById(R.id.tv_location_barcode)
        btnScanSku = findViewById(R.id.btn_scan_sku)
        tvSkuBarcode = findViewById(R.id.tv_sku_barcode)
        etQuantity = findViewById(R.id.et_quantity)
        btnSaveNew = findViewById(R.id.btn_save_new) // "Save" button
        btnCancel = findViewById(R.id.btn_cancel)   // "Cancel" button

        // Initialize FileHandler
        fileHandler = FileHandler(this)

        setupUserDetailsInToolbar() // Set user details in toolbar
        setupClickListeners()
        updateSaveButtonState() // Initial check for button enablement
    }

    private fun setupUserDetailsInToolbar() {
        val loggedInUser = GoStockApp.loggedInUser
        if (loggedInUser != null) {
            tvLoggedInUserMain.text = "${loggedInUser.username}"
            tvLoggedInUserMain.setOnClickListener { view ->
                showUserMenu(view)
            }
            selectedUser = loggedInUser.username // Automatically set the user for stock entry
        } else {
            tvLoggedInUserMain.text = "Not Logged In"
            // If for some reason no user is logged in (shouldn't happen with proper login flow), force logout
            Toast.makeText(this, "User not logged in. Redirecting to login.", Toast.LENGTH_LONG).show()
            performLogout()
        }
    }

    private fun showUserMenu(view: View) {
        val popup = PopupMenu(this, view, Gravity.END)
        popup.menuInflater.inflate(R.menu.user_menu, popup.menu) // Reuses existing menu resource
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_change_password -> {
                    val intent = Intent(this, ChangePasswordActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.action_logout -> {
                    performLogout()
                    true
                }
                else -> false
            }
        }
        popup.show()
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
        // Breadcrumb click listener
        tvBreadcrumbHome.setOnClickListener {
            finish() // Simply finish this activity to go back to HomeActivity
        }

        btnScanLocation.setOnClickListener {
            currentScanType = ScanType.LOCATION
            val intent = Intent(this, BarcodeScannerActivity::class.java)
            barcodeScannerLauncher.launch(intent)
        }

        btnScanSku.setOnClickListener {
            currentScanType = ScanType.SKU
            val intent = Intent(this, BarcodeScannerActivity::class.java)
            barcodeScannerLauncher.launch(intent)
        }

        // TextWatcher for quantity to enable/disable save buttons
        etQuantity.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSaveButtonState()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        // "Save" button behavior: saves and clears fields for a new entry, stays on page
        btnSaveNew.setOnClickListener {
            saveStockEntry(resetFields = true)
        }

        // "Cancel" button behavior: simply goes back without saving
        btnCancel.setOnClickListener {
            finish()
        }
    }

    /**
     * Saves the current stock entry data.
     * @param resetFields If true, clears the input fields after saving.
     */
    private fun saveStockEntry(resetFields: Boolean) {
        // Username is automatically the logged-in user
        val location = tvLocationBarcode.text.toString()
        val sku = tvSkuBarcode.text.toString()
        val quantity = etQuantity.text.toString().toIntOrNull()

        // Validation: Username is implicitly always selected now, but check other fields
        if (selectedUser.isEmpty() || location == "N/A" || sku == "N/A" || quantity == null || quantity <= 0) {
            Toast.makeText(this, "Please complete all fields correctly (Location, SKU, Quantity > 0)", Toast.LENGTH_LONG).show()
            return // Stop the function if validation fails
        }

        // Create a StockEntry object with a new UUID and current timestamp
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val newEntry = StockEntry(
            timestamp = timestamp,
            username = selectedUser, // Use the automatically captured logged-in username
            locationBarcode = location,
            skuBarcode = sku,
            quantity = quantity
        )

        // Save the entry using FileHandler
        fileHandler.addStockEntry(newEntry)

        Toast.makeText(this, "Entry Saved!", Toast.LENGTH_SHORT).show()

        if (resetFields) {
            resetInputFields()
        }
        // IMPORTANT: No 'finish()' call here for "Save" button
    }

    /**
     * Resets the barcode display TextViews and quantity EditText.
     */
    private fun resetInputFields() {
        tvLocationBarcode.text = "N/A" // Reset to "N/A" without prefix
        tvSkuBarcode.text = "N/A" // Reset to "N/A" without prefix
        etQuantity.text.clear() // Clear the text in the quantity input field
        btnScanSku.isEnabled = false // DISABLE SCAN SKU button when fields are reset
        updateSaveButtonState() // Update button state after clearing
    }

    /**
     * Updates the enabled state of the Save buttons based on input validity.
     */
    private fun updateSaveButtonState() {
        // User is implicitly always selected now, so check other fields
        val isLocationScanned = tvLocationBarcode.text.toString() != "N/A"
        val isSkuScanned = tvSkuBarcode.text.toString() != "N/A"
        val isQuantityEntered = etQuantity.text.isNotBlank() && etQuantity.text.toString().toIntOrNull() != null && etQuantity.text.toString().toIntOrNull()!! > 0

        val canSave = isLocationScanned && isSkuScanned && isQuantityEntered
        btnSaveNew.isEnabled = canSave
        btnCancel.isEnabled = true // Cancel button should always be enabled
    }
}