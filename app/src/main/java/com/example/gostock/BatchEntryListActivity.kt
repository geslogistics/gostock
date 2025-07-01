package com.example.gostock

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BatchEntryListActivity : AppCompatActivity() {

    // --- UI Views ---
    private lateinit var btnToolbarBack: ImageButton
    private lateinit var btnToolbarMore: ImageButton
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
    private lateinit var entryAdapter: EntryAdapter
    // CHANGED: Made currentBatch a class property to access it in the delete function
    private var currentBatch: Batch? = null

    companion object {
        const val EXTRA_BATCH_OBJECT = "extra_batch_object"
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

        // Use the safe-let block to ensure currentBatch is not null
        currentBatch?.let {
            populateHeader(it)
            setupRecyclerView(it.entries)
        }
    }

    private fun initViews() {
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)
        btnToolbarMore = findViewById(R.id.btn_toolbar_more)
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

    private fun setupRecyclerView(entries: List<StockEntry>) {
        if (entries.isEmpty()) {
            recyclerView.visibility = View.GONE
            tvNoRecords.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            tvNoRecords.visibility = View.GONE
            val sortedEntries = entries.sortedByDescending { parseTimestamp(it.timestamp) }
            entryAdapter = EntryAdapter(sortedEntries) { clickedEntry ->
                Toast.makeText(this, "Clicked entry with SKU: ${clickedEntry.skuBarcode}", Toast.LENGTH_SHORT).show()
            }
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = entryAdapter
        }
    }

    private fun setupClickListeners() {
        btnToolbarBack.setOnClickListener {
            finish()
        }
        btnToolbarMore.setOnClickListener { view ->
            showMoreMenu(view)
        }
    }

    private fun showMoreMenu(view: View) {
        val popup = PopupMenu(this, view, Gravity.END)
        popup.menuInflater.inflate(R.menu.batch_more_menu, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_delete_batch -> {
                    // Call the confirmation dialog
                    showDeleteConfirmationDialog()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    // --- NEW: Functions for Deleting the Batch ---

    private fun showDeleteConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Deletion")
            .setMessage("Are you sure you want to delete this entire batch and all its entries? This action cannot be undone.")
            .setIcon(R.drawable.ic_delete_icon)
            .setPositiveButton("Delete") { _, _ ->
                deleteCurrentBatch()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteCurrentBatch() {
        val batchIdToDelete = currentBatch?.batch_id
        if (batchIdToDelete == null) {
            Toast.makeText(this, "Error: Could not identify batch to delete.", Toast.LENGTH_SHORT).show()
            return
        }

        val fileHandler = FileHandler(this, "go_data.json")
        val allEntries = fileHandler.loadStockEntries()

        // Filter the list to keep only the entries that are NOT part of the batch being deleted
        val remainingEntries = allEntries.filter { it.batch_id != batchIdToDelete }

        // Save the filtered list back to the file, overwriting the old content
        fileHandler.saveStockEntries(remainingEntries)

        Toast.makeText(this, "Batch '$batchIdToDelete' deleted.", Toast.LENGTH_SHORT).show()

        // Set a result so the previous screen (BatchListActivity) knows to refresh its list
        setResult(Activity.RESULT_OK)
        finish() // Close this activity
    }

    private fun parseTimestamp(timestampStr: String): Long? {
        timestampStr.toLongOrNull()?.let { return it }
        val possibleFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US),
            SimpleDateFormat("MMM dd, yyyy, h:mm:ss a", Locale.US)
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