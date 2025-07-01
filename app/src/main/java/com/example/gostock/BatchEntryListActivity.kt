package com.example.gostock

import android.os.Build
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

class BatchEntryListActivity : AppCompatActivity() {

    private lateinit var btnToolbarBack: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoRecords: TextView
    private lateinit var entryAdapter: EntryAdapter

    // TextViews for the new header
    private lateinit var tvHeaderID: TextView
    private lateinit var tvHeaderSender: TextView
    private lateinit var tvHeaderItemCount: TextView
    private lateinit var tvHeaderTotalQty: TextView
    private lateinit var tvHeaderLocations: TextView
    private lateinit var tvHeaderSkus: TextView
    private lateinit var tvHeaderDuration: TextView
    private lateinit var tvHeaderTransferDate: TextView

    companion object {
        const val EXTRA_BATCH_OBJECT = "extra_batch_object"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch_entry_list)

        initViews()
        setupClickListeners()

        val batch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_BATCH_OBJECT, Batch::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_BATCH_OBJECT)
        }

        if (batch == null) {
            Toast.makeText(this, "Error: Batch data not found.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        populateHeader(batch)
        setupRecyclerView(batch.entries)
    }

    private fun initViews() {
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)
        recyclerView = findViewById(R.id.recyclerView_records)
        tvNoRecords = findViewById(R.id.tv_no_records)

        // Find header views
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

            // We reuse the existing EntryAdapter.
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
