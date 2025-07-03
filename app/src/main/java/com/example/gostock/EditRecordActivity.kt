package com.example.gostock

import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.gson.reflect.TypeToken
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EditRecordActivity : AppCompatActivity() {

    private lateinit var btnToolbarBack: ImageButton
    private lateinit var btnToolbarSave: ImageButton

    // --- UPDATED: Use the new generic JsonFileHandlers ---
    private lateinit var stockEntryFileHandler: JsonFileHandler<StockEntry>
    private lateinit var stockEntryArchivedFileHandler: JsonFileHandler<StockEntryArchived>

    private var currentEntry: StockEntry? = null
    private lateinit var tvTimestamp: TextView
    private lateinit var tvUser: TextView
    private lateinit var tvLocationBarcode: TextView
    private lateinit var tvSkuBarcode: TextView
    private lateinit var etQuantity: EditText
    private lateinit var btnDeleteRecord: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_record)

        // --- UPDATED: Initialize the generic handlers with the correct types ---
        val stockListType = object : TypeToken<MutableList<StockEntry>>() {}
        stockEntryFileHandler = JsonFileHandler(this, "stock_data.json", stockListType)

        val stockArchivedListType = object : TypeToken<MutableList<StockEntryArchived>>() {}
        stockEntryArchivedFileHandler = JsonFileHandler(this, "stock_deleted.json", stockArchivedListType)

        initViews()
        InitiateSettingsElements()

        currentEntry = intent.getParcelableExtra(RecordListActivity.EXTRA_STOCK_ENTRY)

        if (currentEntry == null) {
            Toast.makeText(this, "Error: No record data provided for editing.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        populateFields(currentEntry!!)
        setupClickListeners()
        updateSaveButtonState()
    }

    private fun InitiateSettingsElements() {
        if (AppSettings.locationEditable) {
            tvLocationBarcode.isEnabled = true
        } else {
            tvLocationBarcode.isEnabled = false
        }
        if (AppSettings.skuEditable) {
            tvSkuBarcode.isEnabled = true
        } else {
            tvSkuBarcode.isEnabled = false
        }
    }

    private fun initViews() {
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)
        btnToolbarSave = findViewById(R.id.btn_toolbar_save)
        tvTimestamp = findViewById(R.id.tv_edit_timestamp)
        tvUser = findViewById(R.id.tv_edit_user)
        tvLocationBarcode = findViewById(R.id.tv_edit_location_barcode)
        tvSkuBarcode = findViewById(R.id.tv_edit_sku_barcode)
        etQuantity = findViewById(R.id.et_edit_quantity)
        btnDeleteRecord = findViewById(R.id.btn_delete_record)
    }

    private fun populateFields(entry: StockEntry) {
        // Format the Long timestamp into a readable date string for display
        try {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            tvTimestamp.text = sdf.format(Date(entry.timestamp))
        } catch (e: Exception) {
            tvTimestamp.text = "Invalid Date"
        }

        tvUser.text = entry.username
        tvLocationBarcode.text = entry.locationBarcode
        tvSkuBarcode.text = entry.skuBarcode
        etQuantity.setText(entry.quantity.toString())
    }

    private fun setupClickListeners() {
        btnToolbarBack.setOnClickListener { finish() }
        btnToolbarSave.setOnClickListener { saveChanges() }
        btnDeleteRecord.setOnClickListener { confirmDelete() }

        etQuantity.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSaveButtonState()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun saveChanges() {
        val newQuantity = etQuantity.text.toString().toIntOrNull()
        if (newQuantity == null || newQuantity <= 0) {
            Toast.makeText(this, "Quantity must be a valid number greater than 0.", Toast.LENGTH_SHORT).show()
            return
        }
        currentEntry?.let { original ->
            val updatedEntry = original.copy(quantity = newQuantity, locationBarcode = tvLocationBarcode.text.toString(), skuBarcode = tvSkuBarcode.text.toString())
            stockEntryFileHandler.updateRecord(updatedEntry)
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete Record")
            .setMessage("Are you sure you want to permanently delete this record? This action cannot be undone.")
            .setPositiveButton("Yes") { _, _ ->
                currentEntry?.id?.let { idToDelete ->
                    val entryToDelete = currentEntry!!
                    val actionUser = GoStockApp.loggedInUser?.username ?: "Unknown"
                    val actionTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    // --- FIX: Convert to StockEntryArchived and enrich ---
                    val archivedEntry = StockEntryArchived(
                        id = entryToDelete.id,
                        timestamp = entryToDelete.timestamp,
                        username = entryToDelete.username,
                        locationBarcode = entryToDelete.locationBarcode,
                        skuBarcode = entryToDelete.skuBarcode,
                        quantity = entryToDelete.quantity,
                        action_user = actionUser,
                        action_timestamp = System.currentTimeMillis(),
                        action = "Entry Deleted"
                    )

                    // Save the archived entry to the deleted file
                    stockEntryArchivedFileHandler.addRecord(archivedEntry)

                    // Delete the original entry from stock_data.json
                    stockEntryFileHandler.deleteRecord(idToDelete)

                    setResult(RESULT_OK)
                    Toast.makeText(this, "Record deleted successfully!", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    private fun updateSaveButtonState() {
        val enteredQuantity = etQuantity.text.toString().toIntOrNull()
        val isQuantityValid = enteredQuantity != null && enteredQuantity > 0
        val hasQuantityChanged = enteredQuantity != currentEntry?.quantity
        //btnToolbarSave.isEnabled = isQuantityValid && hasQuantityChanged
    }
}
