package com.example.gostock

import android.app.Activity
import android.content.Intent
import android.net.Uri
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
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class BatchListActivity : AppCompatActivity() {

    private lateinit var btnToolbarBack: ImageButton
    private lateinit var btnToolbarMore: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoBatches: TextView

    // --- UPDATED: Use the new generic JsonFileHandler ---
    private lateinit var goDataFileHandler: JsonFileHandler<BatchEntry>

    private lateinit var batchAdapter: BatchAdapter
    private var batches: MutableList<Batch> = mutableListOf()
    private val TAG = "BatchListActivity"

    // --- ActivityResultLaunchers for file operations ---

    private val exportAllLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val recordsToExport = goDataFileHandler.loadRecords()
                writeAllBatchesToCsv(uri, recordsToExport, "All batches exported successfully!")
            }
        } else {
            Toast.makeText(this, "Export cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    private val exportAndClearAllLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                val recordsToExport = goDataFileHandler.loadRecords()

                // Export the original data first
                val success = writeAllBatchesToCsv(uri, recordsToExport, "All batches exported. Clearing data...")

                if (success) {
                    // If export was successful, enrich and move the data
                    val actionUser = GoStockApp.loggedInUser?.username ?: "Unknown"
                    val actionTimestamp = System.currentTimeMillis()
                    val enrichedRecords = recordsToExport.map{
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
                            action = "Batch Exported and Cleared"
                        )
                    }

                    

                    val stockListType = object : TypeToken<MutableList<BatchEntryArchived>>() {}
                    val deletedFileHandler = JsonFileHandler(this, "go_deleted.json", stockListType)
                    deletedFileHandler.addMultipleRecords(enrichedRecords)

                    goDataFileHandler.clearData()
                    loadAndDisplayBatches()
                }
            }
        } else {
            Toast.makeText(this, "Export & Clear cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                importBatchesFromCsv(uri)
            }
        }
    }

    // --- Activity Lifecycle ---

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch_list)

        initViews()

        // --- UPDATED: Initialize the generic handler with the correct type ---
        val stockListType = object : TypeToken<MutableList<BatchEntry>>() {}
        goDataFileHandler = JsonFileHandler(this, "go_data.json", stockListType)

        setupRecyclerView()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        loadAndDisplayBatches()
    }

    private fun initViews() {
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)
        btnToolbarMore = findViewById(R.id.btn_toolbar_more)
        recyclerView = findViewById(R.id.recyclerView_batches)
        tvNoBatches = findViewById(R.id.tv_no_batches)
    }

    private fun setupRecyclerView() {
        batchAdapter = BatchAdapter(batches) { clickedBatch ->
            val intent = Intent(this, BatchEntryListActivity::class.java).apply {
                putExtra(BatchEntryListActivity.EXTRA_BATCH_OBJECT, clickedBatch)
            }
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = batchAdapter
    }

    private fun setupClickListeners() {
        btnToolbarBack.setOnClickListener { finish() }
        btnToolbarMore.setOnClickListener { view -> showMoreMenu(view) }
    }

    private fun showMoreMenu(view: View) {
        val popup = PopupMenu(this, view, Gravity.END)
        popup.menuInflater.inflate(R.menu.batch_list_more_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_transfer_all_batch -> {
                    startActivity(Intent(this, TransferAllBatchActivity::class.java))
                    true
                }
                R.id.action_export_all_batch -> {
                    initiateAllBatchExport(isClearing = false)
                    true
                }
                R.id.action_export_all_clear_batch -> {
                    initiateAllBatchExport(isClearing = true)
                    true
                }
                R.id.action_import_batch -> {
                    initiateBatchImport()
                    true
                }
                R.id.action_delete_all_batch -> {
                    showDeleteAllConfirmationDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // --- Data Loading and Processing ---

    private fun loadAndDisplayBatches() {
        val allEntries = goDataFileHandler.loadRecords()
        if (allEntries.isEmpty()) {
            recyclerView.visibility = View.GONE
            tvNoBatches.visibility = View.VISIBLE
            batches.clear()
            batchAdapter.updateData(batches)
            return
        }

        val groupedBatches = allEntries
            .filter { !it.batch_id.isNullOrBlank() }
            .groupBy { it.batch_id!! }
            .map { (batchId, entriesInBatch) ->
                val firstEntry = entriesInBatch.first()

                // --- FIX: Use the parseTimestamp helper function to get correct min/max ---
                val timestampsAsLong = entriesInBatch.mapNotNull { it.timestamp }
                val minTimestamp = timestampsAsLong.min()
                val maxTimestamp = timestampsAsLong.max()

                val durationMillis = if (minTimestamp != null && maxTimestamp != null) maxTimestamp - minTimestamp else 0L
                val durationHours = durationMillis / (1000.0f * 60 * 60)
                val uniqueLocations = entriesInBatch.map { it.locationBarcode }.distinct().count()
                val uniqueSkus = entriesInBatch.map { it.skuBarcode }.distinct().count()
                val totalQuantity = entriesInBatch.sumOf { it.quantity }

                Batch(
                    batch_id = batchId,
                    batch_user = firstEntry.batch_user,
                    transfer_date = firstEntry.transfer_date,
                    receiver_user = firstEntry.receiver_user,
                    item_count = entriesInBatch.size,
                    batch_timer = durationHours,
                    locations_counted = uniqueLocations,
                    sku_counted = uniqueSkus,
                    quantity_counted = totalQuantity,
                    entries = entriesInBatch,
                    first_entry_date = minTimestamp,
                    last_entry_date = maxTimestamp
                )
            }
            .sortedByDescending { it.transfer_date }

        batches.clear()
        batches.addAll(groupedBatches)

        if (batches.isEmpty()) {
            recyclerView.visibility = View.GONE
            tvNoBatches.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            tvNoBatches.visibility = View.GONE
            batchAdapter.updateData(batches)
        }
    }

    // --- Export, Import, and Delete Logic ---

    private fun initiateAllBatchExport(isClearing: Boolean) {
        if (batches.isEmpty()) {
            Toast.makeText(this, "No batches to export.", Toast.LENGTH_SHORT).show()
            return
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = if (isClearing) "gostock_all_batches_cleared_$timestamp.csv" else "gostock_all_batches_$timestamp.csv"

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }

        if (isClearing) {
            exportAndClearAllLauncher.launch(intent)
        } else {
            exportAllLauncher.launch(intent)
        }
    }

    private fun writeAllBatchesToCsv(uri: Uri, records: List<BatchEntry>, successMessage: String): Boolean {
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
            contentResolver.openOutputStream(uri)?.use { it.write(csvBuilder.toString().toByteArray()) }
            Toast.makeText(this, successMessage, Toast.LENGTH_LONG).show()
            true
        } catch (e: IOException) {
            Toast.makeText(this, "Failed to write to file.", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun initiateBatchImport() {
        val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        importLauncher.launch(intent)
    }

    private fun importBatchesFromCsv(uri: Uri) {
        val importedEntries = mutableListOf<BatchEntry>()
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readLine() // Skip header
                    var line: String?
                    var lineNumber = 1
                    while (reader.readLine().also { line = it } != null) {
                        lineNumber++
                        val columns = parseCsvLine(line!!)

                        if (columns.size < 10 || columns.take(10).any { it.isBlank() }) {
                            Toast.makeText(this, "Import Rejected: Malformed row found at line $lineNumber. All first 10 fields are required.", Toast.LENGTH_LONG).show()
                            return
                        }

                        try {
                            // --- FIX: Read timestamp as a String, and parse other fields safely ---
                            val entry = BatchEntry(
                                id = columns[0],
                                timestamp = parseTimestamp(columns[1]), // Read as String
                                username = columns[2],
                                locationBarcode = columns[3],
                                skuBarcode = columns[4],
                                quantity = columns[5].toIntOrNull() ?: 0, // Safe parsing
                                batch_id = columns[6],
                                batch_user = columns[7],
                                transfer_date = parseTimestamp(columns[8]), // Safe parsing
                                receiver_user = columns[9]
                            )
                            importedEntries.add(entry)
                        } catch (e: Exception) {
                            Log.e(TAG, "Skipping row due to parsing error: ${e.message}")
                            Toast.makeText(this, "Import Rejected: Error parsing row $lineNumber.", Toast.LENGTH_LONG).show()
                            return
                        }
                    }
                }
            }

            if (importedEntries.isNotEmpty()) {
                goDataFileHandler.addMultipleRecords(importedEntries)
                Toast.makeText(this, "Successfully imported ${importedEntries.size} records!", Toast.LENGTH_LONG).show()
                loadAndDisplayBatches()
            } else {
                Toast.makeText(this, "No valid records found to import.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading or processing CSV file.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showDeleteAllConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Delete All Batches")
            .setMessage("Are you sure you want to delete ALL batch records? This action will move them to the deleted records file and cannot be undone.")
            .setIcon(R.drawable.ic_delete_icon)
            .setPositiveButton("Delete All") { _, _ -> deleteAllBatches(showToast = true) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteAllBatches(showToast: Boolean) {
        val allEntries = goDataFileHandler.loadRecords()
        if (allEntries.isEmpty()) {
            if(showToast) Toast.makeText(this, "There is nothing to delete.", Toast.LENGTH_SHORT).show()
            return
        }

        val actionUser = GoStockApp.loggedInUser?.username ?: "Unknown"
        val actionTimestamp = System.currentTimeMillis()
        val enrichedEntries = allEntries.map {
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
                action = "Batch Deleted"
            )
        }

        val stockListType = object : TypeToken<MutableList<BatchEntryArchived>>() {}
        val deletedFileHandler = JsonFileHandler(this, "go_deleted.json", stockListType)
        deletedFileHandler.addMultipleRecords(enrichedEntries)

        goDataFileHandler.clearData()

        if (showToast) {
            Toast.makeText(this, "All batch data cleared and archived.", Toast.LENGTH_SHORT).show()
        }
        loadAndDisplayBatches()
    }

    // --- Helper Functions ---
    private fun escapeCsv(field: String): String = if (field.contains(",") || field.contains("\"") || field.contains("\n")) "\"${field.replace("\"", "\"\"")}\"" else field

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var currentPos = 0
        var inQuotes = false
        var fieldStart = 0
        while (currentPos < line.length) {
            when (line[currentPos]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) {
                    result.add(line.substring(fieldStart, currentPos).replace("\"\"", "\"").removeSurrounding("\""))
                    fieldStart = currentPos + 1
                }
            }
            currentPos++
        }
        result.add(line.substring(fieldStart).replace("\"\"", "\"").removeSurrounding("\""))
        return result
    }

    private fun parseTimestamp(dateString: String): Long {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return try {
            val date = sdf.parse(dateString)
            date?.time ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }
}
