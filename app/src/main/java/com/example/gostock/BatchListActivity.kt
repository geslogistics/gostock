package com.example.gostock

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class BatchListActivity : AppCompatActivity() {

    private lateinit var btnToolbarBack: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoBatches: TextView
    private lateinit var fileHandler: FileHandler
    private lateinit var batchAdapter: BatchAdapter
    private var batches: MutableList<Batch> = mutableListOf()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch_list)

        btnToolbarBack = findViewById(R.id.btn_toolbar_back)
        recyclerView = findViewById(R.id.recyclerView_batches)
        tvNoBatches = findViewById(R.id.tv_no_batches)

        fileHandler = FileHandler(this, "go_data.json")

        setupRecyclerView()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        loadAndDisplayBatches()
    }

    /**
     * This is the function that has been updated.
     * The click listener now opens the new activity and passes the full Batch object.
     */
    private fun setupRecyclerView() {
        batchAdapter = BatchAdapter(batches) { clickedBatch ->
            // This is the new logic.
            // When a batch is clicked, open the new BatchEntryListActivity.
            val intent = Intent(this, BatchEntryListActivity::class.java).apply {
                // The entire Batch object is Parcelable, so we can pass it directly.
                putExtra(BatchEntryListActivity.EXTRA_BATCH_OBJECT, clickedBatch)
            }
            startActivity(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = batchAdapter
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
            } catch (e: Exception) {
                // Ignore and try the next format
            }
        }
        Log.e("BatchListActivity", "Could not parse timestamp string: '$timestampStr'")
        return null
    }

    private fun loadAndDisplayBatches() {
        val allEntries = fileHandler.loadStockEntries()

        if (allEntries.isEmpty()) {
            recyclerView.visibility = View.GONE
            tvNoBatches.visibility = View.VISIBLE
            return
        }

        val groupedBatches = allEntries
            .filter { !it.batch_id.isNullOrBlank() }
            .groupBy { it.batch_id!! }
            .map { (batchId, entriesInBatch) ->
                val firstEntry = entriesInBatch.first()
                val timestampsAsLong = entriesInBatch.mapNotNull { parseTimestamp(it.timestamp) }
                val minTimestamp = timestampsAsLong.minOrNull()
                val maxTimestamp = timestampsAsLong.maxOrNull()

                val durationMillis = if (minTimestamp != null && maxTimestamp != null) {
                    maxTimestamp - minTimestamp
                } else {
                    0L
                }

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

    private fun setupClickListeners() {
        btnToolbarBack.setOnClickListener {
            finish()
        }
    }
}
