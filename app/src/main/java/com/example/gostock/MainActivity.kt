package com.example.gostock

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity(), ZebraScanResultListener {

    private lateinit var tvRecentEntriesTitle: TextView
    private lateinit var rvRecentEntries: RecyclerView
    private lateinit var tvNoRecentEntries: TextView
    private lateinit var recentEntryAdapter: RecentEntryAdapter

    private lateinit var btnToolbarSave: ImageButton
    private lateinit var btnToolbarBack: ImageButton
    private lateinit var btnToolbarClear: ImageButton

    private lateinit var cardLocation: MaterialCardView
    private lateinit var tvLocationValue: TextView
    private lateinit var ivLocationCheckIcon: ImageView

    private lateinit var cardSku: MaterialCardView
    private lateinit var tvSkuValue: TextView
    private lateinit var ivSkuCheckIcon: ImageView

    private lateinit var cardQuantity: MaterialCardView
    private lateinit var etQuantity: EditText

    private var selectedUser: String = ""
    // --- UPDATED: Use the new generic JsonFileHandler ---
    private lateinit var stockFileHandler: JsonFileHandler<StockEntry>

    enum class ScanType { LOCATION, SKU, NONE }
    private var currentScanType: ScanType = ScanType.NONE

    private var isZebraDevice = false
    private lateinit var zebraScannerHelper: ZebraScannerHelper

    private val TAG = "MainActivity"

    private val barcodeScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val scannedBarcode = result.data?.getStringExtra(BarcodeScannerActivity.EXTRA_BARCODE_RESULT)
            val scannedSymbology = result.data?.getStringExtra(BarcodeScannerActivity.EXTRA_SYMBOLOGY_TYPE)
            processCameraScanResult(scannedBarcode, scannedSymbology)
        } else {
            if (currentScanType == ScanType.LOCATION) {
                tvLocationValue.text = ""
                ivLocationCheckIcon.visibility = View.GONE
                cardSku.isEnabled = false
            } else if (currentScanType == ScanType.SKU) {
                tvSkuValue.text = ""
                ivSkuCheckIcon.visibility = View.GONE
            }
            showSnackbar("Barcode scan cancelled or failed.", Snackbar.LENGTH_SHORT)
            updateSaveButtonState()
        }
        currentScanType = ScanType.NONE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()

        // --- UPDATED: Initialize the generic handler with the correct type ---
        val stockListType = object : TypeToken<MutableList<StockEntry>>() {}
        stockFileHandler = JsonFileHandler(this, "stock_data.json", stockListType)

        GoStockApp.loggedInUser?.let {
            selectedUser = it.username
        } ?: run {
            showSnackbar("User not logged in. Redirecting to login.", Snackbar.LENGTH_LONG)
            performLogout()
            return
        }

        isZebraDevice = Build.MANUFACTURER.equals("Zebra Technologies", ignoreCase = true) ||
                Build.MODEL.startsWith("TC", ignoreCase = true) ||
                Build.MODEL.startsWith("MC", ignoreCase = true) ||
                Build.MODEL.startsWith("ET", ignoreCase = true)

        zebraScannerHelper = ZebraScannerHelper(this, this)

        if (isZebraDevice && AppSettings.enableZebraDevice) {
            zebraScannerHelper.setupDataWedgeProfile()
            zebraScannerHelper.registerReceiver()
            showSnackbar("Zebra DataWedge scanner enabled.", Snackbar.LENGTH_SHORT)
        } else {
            showSnackbar("Using camera for scanning.", Snackbar.LENGTH_SHORT)
        }

        setupRecentEntriesRecyclerView()
        setupClickListeners()
        resetInputFields()
        updateSaveButtonState()
    }

    override fun onResume() {
        super.onResume()
        if (isZebraDevice && AppSettings.enableZebraDevice) {
            zebraScannerHelper.activateProfile(DataWedgeConstants.PROFILE_NAME)
            zebraScannerHelper.enableBarcodePlugin()
        }
        loadRecentEntries()
    }

    override fun onPause() {
        super.onPause()
        if (isZebraDevice && AppSettings.enableZebraDevice) {
            zebraScannerHelper.stopSoftScan()
            zebraScannerHelper.disableBarcodePlugin()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isZebraDevice && AppSettings.enableZebraDevice) {
            try {
                zebraScannerHelper.unregisterReceiver()
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering DataWedge receiver: ${e.message}")
            }
            zebraScannerHelper.activateProfile("")
        }
    }

    private fun initViews() {
        btnToolbarSave = findViewById(R.id.btn_toolbar_save)
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)
        btnToolbarClear = findViewById(R.id.btn_toolbar_clear)
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
    }

    private fun setupRecentEntriesRecyclerView() {
        recentEntryAdapter = RecentEntryAdapter(emptyList())
        rvRecentEntries.layoutManager = LinearLayoutManager(this)
        rvRecentEntries.adapter = recentEntryAdapter
    }

    private fun loadRecentEntries() {
        // --- UPDATED: Use the new handler's method name ---
        val allEntries = stockFileHandler.loadRecords()
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

    private fun setupClickListeners() {
        btnToolbarBack.setOnClickListener { finish() }
        btnToolbarClear.setOnClickListener {
            resetInputFields()
            showSnackbar("Fields cleared.", Snackbar.LENGTH_SHORT)
        }
        btnToolbarSave.setOnClickListener { saveStockEntry(resetFields = true) }

        cardLocation.setOnClickListener {
            if (isZebraDevice && AppSettings.enableZebraDevice) {
                showSnackbar("Scan Location with physical scanner.", Snackbar.LENGTH_SHORT)
                currentScanType = ScanType.LOCATION
            } else {
                currentScanType = ScanType.LOCATION
                barcodeScannerLauncher.launch(Intent(this, BarcodeScannerActivity::class.java))
            }
        }

        cardSku.setOnClickListener {
            if (!cardSku.isEnabled) {
                showSnackbar("Please scan Location first.", Snackbar.LENGTH_SHORT)
                return@setOnClickListener
            }
            if (isZebraDevice && AppSettings.enableZebraDevice) {
                showSnackbar("Scan SKU with physical scanner.", Snackbar.LENGTH_SHORT)
                currentScanType = ScanType.SKU
            } else {
                currentScanType = ScanType.SKU
                barcodeScannerLauncher.launch(Intent(this, BarcodeScannerActivity::class.java))
            }
        }

        etQuantity.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSaveButtonState()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun saveStockEntry(resetFields: Boolean) {
        val location = tvLocationValue.text.toString()
        val sku = tvSkuValue.text.toString()
        val quantity = etQuantity.text.toString().toIntOrNull()

        if (location.isEmpty() || sku.isEmpty() || quantity == null || quantity <= 0) {
            showSnackbar("Please complete all fields correctly (Location, SKU, Quantity > 0)", Snackbar.LENGTH_LONG)
            return
        }

        //val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

        val newEntry = StockEntry(

            timestamp = System.currentTimeMillis(),
            username = selectedUser,
            locationBarcode = location,
            skuBarcode = sku,
            quantity = quantity
        )

        // --- UPDATED: Use the new handler's method name ---
        stockFileHandler.addRecord(newEntry)
        showSnackbar("Entry Saved!", Snackbar.LENGTH_SHORT)

        if (resetFields) {
            resetInputFields()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(etQuantity.windowToken, 0)
        }
        loadRecentEntries()
    }

    private fun resetInputFields() {
        tvLocationValue.text = ""
        ivLocationCheckIcon.visibility = View.GONE
        tvSkuValue.text = ""
        ivSkuCheckIcon.visibility = View.GONE
        etQuantity.text.clear()
        cardSku.isEnabled = false
        updateSaveButtonState()
    }

    private fun updateSaveButtonState() {
        val isLocationScanned = tvLocationValue.text.isNotEmpty()
        val isSkuScanned = tvSkuValue.text.isNotEmpty()
        val isQuantityEntered = etQuantity.text.isNotBlank() && etQuantity.text.toString().toIntOrNull() != null && etQuantity.text.toString().toInt() > 0
        btnToolbarSave.isEnabled = isLocationScanned && isSkuScanned && isQuantityEntered
    }

    // --- ZebraScanResultListener Implementation ---
    override fun onZebraScanResult(scanData: String?, symbology: String?) {
        processZebraScanResult(scanData, symbology)
    }

    override fun onZebraScanError(errorMessage: String) {
        showSnackbar("Zebra Scan Error: $errorMessage", Snackbar.LENGTH_LONG)
    }

    private fun processZebraScanResult(scanData: String?, scannedSymbology: String?) {
        scanData?.let {
            val scanPurpose: String
            val acceptedFormats: Set<String>

            if (tvLocationValue.text.isEmpty()) {
                scanPurpose = "Location"
                acceptedFormats = AppSettings.acceptedLocationFormats
            } else if (tvSkuValue.text.isEmpty()) {
                scanPurpose = "SKU"
                acceptedFormats = AppSettings.acceptedSkuFormats
            } else {
                showSnackbar("Location and SKU already scanned. Save or clear to continue.", Snackbar.LENGTH_LONG)
                return
            }

            if (!AppSettings.isFormatAccepted(scannedSymbology, acceptedFormats)) {
                val acceptedFormatsString = if (acceptedFormats.isEmpty()) "any format" else acceptedFormats.joinToString(", ")
                showSnackbar("Scanned $scanPurpose barcode format ($scannedSymbology) is not accepted. Only $acceptedFormatsString barcodes are accepted for $scanPurpose.", Snackbar.LENGTH_LONG)
                return
            }

            if (scanPurpose == "Location") {
                tvLocationValue.text = it
                ivLocationCheckIcon.visibility = View.VISIBLE
                cardSku.isEnabled = true
            } else { // SKU
                tvSkuValue.text = it
                ivSkuCheckIcon.visibility = View.VISIBLE
                etQuantity.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                Handler(Looper.getMainLooper()).postDelayed({
                    imm.showSoftInput(etQuantity, InputMethodManager.SHOW_IMPLICIT)
                }, 200)
            }
            updateSaveButtonState()
        }
    }

    private fun processCameraScanResult(scanData: String?, scannedSymbology: String?) {
        scanData?.let {
            val scanPurpose = if (currentScanType == ScanType.LOCATION) "Location" else "SKU"
            val acceptedFormats = if (currentScanType == ScanType.LOCATION) AppSettings.acceptedLocationFormats else AppSettings.acceptedSkuFormats

            if (!AppSettings.isFormatAccepted(scannedSymbology, acceptedFormats)) {
                val acceptedFormatsString = if (acceptedFormats.isEmpty()) "any format" else acceptedFormats.joinToString(", ")
                showSnackbar("Scanned $scanPurpose barcode format ($scannedSymbology) is not accepted. Only $acceptedFormatsString barcodes are accepted for $scanPurpose.", Snackbar.LENGTH_LONG)
                return
            }

            if (currentScanType == ScanType.LOCATION) {
                tvLocationValue.text = it
                ivLocationCheckIcon.visibility = View.VISIBLE
                cardSku.isEnabled = true
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
        }
        currentScanType = ScanType.NONE
    }

    private fun performLogout() {
        (application as GoStockApp).clearLoginSession()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_LONG) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, duration)
        val snackbarTextView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        snackbarTextView.maxLines = 5
        snackbarTextView.ellipsize = null
        snackbar.show()
    }
}
