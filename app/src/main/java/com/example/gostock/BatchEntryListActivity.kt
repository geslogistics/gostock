package com.example.gostock

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BatchEntryListActivity : AppCompatActivity() {

    // --- UI Views ---
    private lateinit var btnToolbarBack: ImageButton
    private lateinit var btnToolbarMore: ImageButton
    private lateinit var pageTitle: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoRecords: TextView

    // --- Header Views ---
    private lateinit var tvHeaderID: TextView
    private lateinit var tvHeaderSender: TextView
    private lateinit var tvHeaderItemCount: TextView
    private lateinit var tvHeaderTotalQty: TextView
    private lateinit var tvHeaderLocations: TextView
    private lateinit var tvHeaderSkus: TextView
    private lateinit var tvHeaderDuration: TextView
    private lateinit var tvHeaderTransferDate: TextView

    // --- Data ---
    private lateinit var batchEntryAdapter: BatchEntryAdapter
    private var currentBatch: Batch? = null

    companion object {
        const val EXTRA_BATCH_OBJECT = "extra_batch_object"
    }

    // --- ActivityResultLaunchers ---
    private val exportBatchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                currentBatch?.let { batch ->
                    writeBatchToCsv(uri, batch.entries, "Batch exported successfully!")
                }
            }
        } else {
            Toast.makeText(this, "Export cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    private val exportAndClearBatchLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                currentBatch?.let { batch ->
                    val exportSuccess = writeBatchToCsv(uri, batch.entries, "Batch exported. Now clearing...")
                    if (exportSuccess) {
                        deleteCurrentBatch(showToast = true, action = "Batch Exported and Cleared")
                    }
                }
            }
        } else {
            Toast.makeText(this, "Export & Clear cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch_entry_list)

        initViews()
        setupClickListeners()

        currentBatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_BATCH_OBJECT, Batch::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_BATCH_OBJECT)
        }

        if (currentBatch == null) {
            Toast.makeText(this, "Error: Batch data not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        currentBatch?.let {
            populateHeader(it)
            setupRecyclerView(it.entries)
        }
    }

    private fun initViews() {
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)
        btnToolbarMore = findViewById(R.id.btn_toolbar_more)
        pageTitle = findViewById(R.id.page_title)
        recyclerView = findViewById(R.id.recyclerView_records)
        tvNoRecords = findViewById(R.id.tv_no_records)

        tvHeaderID = findViewById(R.id.tv_header_batch_id)
        tvHeaderSender = findViewById(R.id.tv_header_sender)
        tvHeaderItemCount = findViewById(R.id.tv_header_item_count)
        tvHeaderTotalQty = findViewById(R.id.tv_header_total_qty)
        tvHeaderLocations = findViewById(R.id.tv_header_locations)
        tvHeaderSkus = findViewById(R.id.tv_header_skus)
        tvHeaderDuration = findViewById(R.id.tv_header_duration)
        tvHeaderTransferDate = findViewById(R.id.tv_header_transfer_date)
    }

    private fun populateHeader(batch: Batch) {
        tvHeaderID.text = batch.batch_id
        tvHeaderSender.text = "User: ${batch.batch_user ?: "N/A"}"
        tvHeaderItemCount.text = "Counter: ${batch.item_count}"
        tvHeaderDuration.text = "Timer: ${String.format("%.2f", batch.batch_timer)} hrs"
        tvHeaderLocations.text = "Locations: ${batch.locations_counted}"
        tvHeaderTotalQty.text = "Total Qty: ${batch.quantity_counted}"
        tvHeaderSkus.text = "SKUs: ${batch.sku_counted}"

        batch.transfer_date?.let {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            tvHeaderTransferDate.text = "Date: ${sdf.format(Date(it))}"
        } ?: run {
            tvHeaderTransferDate.text = "Date: N/A"
        }
    }

    private fun setupRecyclerView(entries: List<BatchEntry>) {
        if (entries.isEmpty()) {
            recyclerView.visibility = View.GONE
            tvNoRecords.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            tvNoRecords.visibility = View.GONE
            val sortedEntries = entries.sortedByDescending { it.timestamp }
            batchEntryAdapter = BatchEntryAdapter(sortedEntries) { clickedEntry ->
                Toast.makeText(this, "Clicked entry with SKU: ${clickedEntry.skuBarcode}", Toast.LENGTH_SHORT).show()
            }
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = batchEntryAdapter
        }
    }

    private fun setupClickListeners() {
        btnToolbarBack.setOnClickListener { finish() }
        btnToolbarMore.setOnClickListener { view -> showMoreMenu(view) }
    }

    private fun showMoreMenu(view: View) {
        val batchIdToHandle = currentBatch?.batch_id
        val popup = PopupMenu(this, view, Gravity.END)
        popup.menuInflater.inflate(R.menu.batch_more_menu, popup.menu)

        // --- NEW: Add this logic to control visibility ---
        val userRole = GoStockApp.loggedInUser?.role

        // Find each menu item by its ID
        val sendItem = popup.menu.findItem(R.id.action_send_batch)
        val exportItem = popup.menu.findItem(R.id.action_export_batch)
        val exportClearItem = popup.menu.findItem(R.id.action_export_clear_batch)
        val deleteItem = popup.menu.findItem(R.id.action_delete_batch)

        // Rule: Transfer is allowed for Admin, Supervisor, and Team Leader
        sendItem.isVisible = (userRole == UserRole.ADMIN || userRole == UserRole.SUPERVISOR || userRole == UserRole.TEAMLEADER)

        // Rule: Export and Export & Clear are allowed for Admin and Team Leader
        val canExport = (userRole == UserRole.ADMIN || userRole == UserRole.TEAMLEADER)
        exportItem.isVisible = canExport
        exportClearItem.isVisible = canExport

        // Rule: Delete is only allowed for Admin
        deleteItem.isVisible = (userRole == UserRole.ADMIN)
        // --- END OF NEW LOGIC ---

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_send_batch -> {
                    val intent = Intent(this, BluetoothBatchSendActivity::class.java).apply {
                        putExtra(BluetoothBatchSendActivity.EXTRA_BATCH_TO_TRANSFER, currentBatch)


                    }
                    startActivity(intent)
                    true
                }
                R.id.action_export_batch -> {
                    initiateBatchExport()
                    true
                }
                R.id.action_export_clear_batch -> {
                    initiateBatchExportAndClear()
                    true
                }
                R.id.action_delete_batch -> {
                    showDeleteConfirmationDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun initiateBatchExport() {
        val batch = currentBatch ?: return
        if (batch.entries.isEmpty()) {
            Toast.makeText(this, "This batch has no entries to export.", Toast.LENGTH_SHORT).show()
            return
        }
        val csvFileName = "batch_${batch.batch_id}.csv"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, csvFileName)
        }
        exportBatchLauncher.launch(intent)
    }

    private fun initiateBatchExportAndClear() {
        val batch = currentBatch ?: return
        if (batch.entries.isEmpty()) {
            Toast.makeText(this, "This batch has no entries to export.", Toast.LENGTH_SHORT).show()
            return
        }
        val csvFileName = "batch_${batch.batch_id}_cleared.csv"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, csvFileName)
        }
        exportAndClearBatchLauncher.launch(intent)
    }

    private fun writeBatchToCsv(uri: Uri, records: List<BatchEntry>, successMessage: String): Boolean {
        val csvBuilder = StringBuilder()
        csvBuilder.append("ID,Timestamp,Username,LocationBarcode,SkuBarcode,Quantity,BatchID,Sender,TransferDate,Receiver,ActionUser,ActionTimestamp,Action\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        for (record in records) {
            val formattedTransferDate = record.transfer_date?.let { sdf.format(Date(it)) } ?: ""
            val formattedTimestamp = record.timestamp?.let { sdf.format(Date(it)) } ?: ""
            csvBuilder.append("${escapeCsv(record.id)},")
            csvBuilder.append("${escapeCsv(formattedTimestamp)},")
            csvBuilder.append("${escapeCsv(record.username)},")
            csvBuilder.append("${escapeCsv(record.locationBarcode)},")
            csvBuilder.append("${escapeCsv(record.skuBarcode)},")
            csvBuilder.append("${record.quantity},")
            csvBuilder.append("${escapeCsv(record.batch_id ?: "")},")
            csvBuilder.append("${escapeCsv(record.batch_user ?: "")},")
            csvBuilder.append("${escapeCsv(formattedTransferDate)},")
            csvBuilder.append("${escapeCsv(record.receiver_user ?: "")}\n")
        }

        return try {
            contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(csvBuilder.toString().toByteArray())
                Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()
                true
            } ?: false
        } catch (e: IOException) {
            Toast.makeText(this, "Failed to write to file.", Toast.LENGTH_LONG).show()
            e.printStackTrace()
            false
        }
    }

    private fun escapeCsv(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Deletion")
            .setMessage("Are you sure you want to delete this entire batch and all its entries? This action cannot be undone.")
            .setIcon(R.drawable.ic_delete_icon)
            .setPositiveButton("Delete") { _, _ -> deleteCurrentBatch(showToast = true, action = "Batch Deleted") }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCurrentBatch(showToast: Boolean, action: String) {
        val batchIdToDelete = currentBatch?.batch_id
        if (batchIdToDelete == null) {
            Toast.makeText(this, "Error: Could not identify batch to delete.", Toast.LENGTH_SHORT).show()
            return
        }

        // --- UPDATED: Use the new generic JsonFileHandler ---
        val stockListType = object : TypeToken<MutableList<BatchEntry>>() {}
        val goDataFileHandler = JsonFileHandler(this, "go_data.json", stockListType)
        val allEntries = goDataFileHandler.loadRecords()

        val entriesToDelete = allEntries.filter { it.batch_id == batchIdToDelete }
        val remainingEntries = allEntries.filter { it.batch_id != batchIdToDelete }

        val actionUser = GoStockApp.loggedInUser?.username ?: "Unknown"
        val actionTimestamp = System.currentTimeMillis()
        val enrichedDeletedEntries = entriesToDelete.map {
            BatchEntryArchived(
                id = it.id,
                timestamp = it.timestamp,
                username = it.username,
                locationBarcode = it.locationBarcode,
                skuBarcode = it.skuBarcode,
                quantity = it.quantity,
                batch_id = it.batch_id,
                batch_user = it.batch_user,
                transfer_date = it.transfer_date,
                receiver_user = it.receiver_user,
                action_user = actionUser,
                action_timestamp = actionTimestamp,
                action = action
            )
        }


        if (enrichedDeletedEntries.isNotEmpty()) {
            val deletedStockListType = object : TypeToken<MutableList<BatchEntryArchived>>() {}
            val deletedFileHandler = JsonFileHandler(this, "go_deleted.json", deletedStockListType)
            deletedFileHandler.addMultipleRecords(enrichedDeletedEntries)
        }

        goDataFileHandler.saveRecords(remainingEntries)

        if (showToast) {
            Toast.makeText(this, "Batch '$batchIdToDelete' deleted.", Toast.LENGTH_SHORT).show()
        }

        setResult(Activity.RESULT_OK)
        finish()
    }

    private fun parseTimestamp(timestampStr: String): Long? {
        timestampStr.toLongOrNull()?.let { return it }
        val possibleFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("MMM dd, Regency, h:mm:ss a", Locale.US)
        )
        for (format in possibleFormats) {
            try {
                format.parse(timestampStr)?.let { return it.time }
            } catch (e: Exception) { /* Ignore */ }
        }
        Log.e("BatchEntryListActivity", "Could not parse timestamp string: '$timestampStr'")
        return null
    }
}
