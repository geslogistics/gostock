package com.example.gostock

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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

    private fun setupRecyclerView() {
        batchAdapter = BatchAdapter(batches) { clickedBatch ->
            Toast.makeText(this, "Batch ${clickedBatch.batch_id} clicked with ${clickedBatch.item_count} items.", Toast.LENGTH_SHORT).show()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = batchAdapter
    }

    /**
     * This function now correctly calculates all the required batch statistics.
     */
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

                // --- NEW COMPUTATION LOGIC ---

                // 1. Calculate Batch Timer
                val timestampsAsLong = entriesInBatch.mapNotNull { it.timestamp.toLongOrNull() }
                val minTimestamp = timestampsAsLong.minOrNull() ?: 0L
                val maxTimestamp = timestampsAsLong.maxOrNull() ?: 0L
                val durationMillis = maxTimestamp - minTimestamp
                // Convert milliseconds to hours as a float, rounded to 2 decimal places
                val durationHours = (TimeUnit.MILLISECONDS.toMinutes(durationMillis) / 60.0).toFloat()

                // 2. Count Unique Locations
                val uniqueLocations = entriesInBatch.map { it.locationBarcode }.distinct().count()

                // 3. Count Unique SKUs
                val uniqueSkus = entriesInBatch.map { it.skuBarcode }.distinct().count()

                // 4. Sum all Quantities
                val totalQuantity = entriesInBatch.sumOf { it.quantity }
                // --- END OF NEW LOGIC ---

                // Construct the Batch object with all the computed data
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
                    entries = entriesInBatch
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
