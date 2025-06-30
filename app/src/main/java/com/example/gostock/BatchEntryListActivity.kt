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
import java.util.Locale

class BatchEntryListActivity : AppCompatActivity() {

    private lateinit var btnToolbarBack: ImageButton
    private lateinit var pageTitle: TextView
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoRecords: TextView
    private lateinit var entryAdapter: EntryAdapter
    private var entries: ArrayList<StockEntry> = arrayListOf()

    companion object {
        const val EXTRA_BATCH_ENTRIES = "extra_batch_entries"
        const val EXTRA_BATCH_ID = "extra_batch_id"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_batch_entry_list)

        btnToolbarBack = findViewById(R.id.btn_toolbar_back)
        pageTitle = findViewById(R.id.page_title)
        recyclerView = findViewById(R.id.recyclerView_records)
        tvNoRecords = findViewById(R.id.tv_no_records)

        val batchId = intent.getStringExtra(EXTRA_BATCH_ID)
        pageTitle.text = "Entries for: $batchId"

        entries = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_BATCH_ENTRIES, StockEntry::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_BATCH_ENTRIES)
        } ?: arrayListOf()

        // --- NEW: Sort the entries by timestamp in descending order ---
        entries.sortByDescending { parseTimestamp(it.timestamp) }
        // --- END OF NEW LOGIC ---

        setupRecyclerView()
        displayEntries()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        entryAdapter = EntryAdapter(entries) { clickedEntry ->
            Toast.makeText(this, "Clicked entry with SKU: ${clickedEntry.skuBarcode}", Toast.LENGTH_SHORT).show()
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = entryAdapter
    }

    private fun displayEntries() {
        if (entries.isEmpty()) {
            recyclerView.visibility = View.GONE
            tvNoRecords.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            tvNoRecords.visibility = View.GONE
            entryAdapter.updateData(entries)
        }
    }

    private fun setupClickListeners() {
        btnToolbarBack.setOnClickListener {
            finish()
        }
    }

    /**
     * Helper function to parse different possible timestamp formats.
     */
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
        Log.e("BatchEntryListActivity", "Could not parse timestamp string: '$timestampStr'")
        return null
    }
}
