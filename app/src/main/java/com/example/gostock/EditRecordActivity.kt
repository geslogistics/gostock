package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog

class EditRecordActivity : AppCompatActivity() {

    private lateinit var fileHandler: FileHandler
    private var currentEntry: StockEntry? = null // Holds the StockEntry object being edited

    private lateinit var tvTimestamp: TextView
    private lateinit var tvUser: TextView
    private lateinit var tvLocationBarcode: TextView
    private lateinit var tvSkuBarcode: TextView
    private lateinit var etQuantity: EditText
    private lateinit var btnSaveChanges: Button
    private lateinit var btnCancelEdit: Button
    private lateinit var btnDeleteRecord: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_record)

        fileHandler = FileHandler(this)

        // Initialize UI elements
        tvTimestamp = findViewById(R.id.tv_edit_timestamp)
        tvUser = findViewById(R.id.tv_edit_user)
        tvLocationBarcode = findViewById(R.id.tv_edit_location_barcode)
        tvSkuBarcode = findViewById(R.id.tv_edit_sku_barcode)
        etQuantity = findViewById(R.id.et_edit_quantity)
        btnSaveChanges = findViewById(R.id.btn_save_changes)
        btnCancelEdit = findViewById(R.id.btn_cancel_edit)
        btnDeleteRecord = findViewById(R.id.btn_delete_record)

        // Retrieve the StockEntry object passed from RecordListActivity
        currentEntry = intent.getParcelableExtra(RecordListActivity.EXTRA_STOCK_ENTRY) // Use getParcelableExtra

        if (currentEntry == null) {
            Toast.makeText(this, "Error: No record data provided for editing.", Toast.LENGTH_LONG).show()
            finish() // Close activity if no data
            return
        }

        populateFields(currentEntry!!) // Populate UI with existing data
        setupClickListeners()
        updateSaveButtonState() // Initial check for save button enablement
    }

    private fun populateFields(entry: StockEntry) {
        tvTimestamp.text = "Timestamp: ${entry.timestamp}"
        tvUser.text = "User: ${entry.username}"
        tvLocationBarcode.text = "Scanned Location: ${entry.locationBarcode}"
        tvSkuBarcode.text = "Scanned SKU: ${entry.skuBarcode}"
        etQuantity.setText(entry.quantity.toString()) // Set existing quantity
    }

    private fun setupClickListeners() {
        // Listener for quantity text changes to enable/disable save buttons
        etQuantity.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateSaveButtonState() // Re-evaluate save button state on text change
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        btnSaveChanges.setOnClickListener {
            saveChanges()
        }

        btnCancelEdit.setOnClickListener {
            setResult(RESULT_CANCELED) // Indicate that editing was cancelled
            finish() // Close activity
        }

        btnDeleteRecord.setOnClickListener {
            confirmDelete()
        }
    }

    private fun saveChanges() {
        val newQuantity = etQuantity.text.toString().toIntOrNull()

        if (newQuantity == null || newQuantity <= 0) {
            Toast.makeText(this, "Quantity must be a valid number greater than 0.", Toast.LENGTH_SHORT).show()
            return
        }

        currentEntry?.let { original ->
            // Create an updated StockEntry object with the new quantity
            val updatedEntry = original.copy(quantity = newQuantity)
            fileHandler.updateStockEntry(updatedEntry) // Use FileHandler to update
            setResult(RESULT_OK) // Indicate success
            finish() // Close activity
        } ?: run {
            Toast.makeText(this, "Error: Original record not found for update.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete Record")
            .setMessage("Are you sure you want to permanently delete this record? This action cannot be undone.")
            .setPositiveButton("Yes") { dialog, which ->
                currentEntry?.id?.let { id ->
                    fileHandler.deleteStockEntry(id) // Use FileHandler to delete
                    setResult(RESULT_OK) // Indicate success (record was deleted)
                    Toast.makeText(this, "Record deleted successfully!", Toast.LENGTH_SHORT).show()
                    finish() // Close activity
                }
            }
            .setNegativeButton("No", null)
            .show()
    }

    /**
     * Updates the enabled state of the Save Changes button.
     * The button is enabled if the quantity is valid AND it's different from the original quantity.
     */
    private fun updateSaveButtonState() {
        val enteredQuantity = etQuantity.text.toString().toIntOrNull()
        val isQuantityValid = enteredQuantity != null && enteredQuantity > 0
        val hasQuantityChanged = enteredQuantity != currentEntry?.quantity // Check if quantity has changed

        btnSaveChanges.isEnabled = isQuantityValid && hasQuantityChanged
    }
}