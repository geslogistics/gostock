package com.example.gostock

import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

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

        // Retrieve the data passed from BatchListActivity
        val batchId = intent.getStringExtra(EXTRA_BATCH_ID)
        pageTitle.text = "Entries for: $batchId" // Set the title dynamically

        entries = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableArrayListExtra(EXTRA_BATCH_ENTRIES, StockEntry::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableArrayListExtra(EXTRA_BATCH_ENTRIES)
        } ?: arrayListOf()

        setupRecyclerView()
        displayEntries()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        // We reuse the existing EntryAdapter, which is great!
        entryAdapter = EntryAdapter(entries) { clickedEntry ->
            // As requested, just show a toast for now.
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
            // The adapter already has the data from its constructor, but we call
            // updateData to be safe and consistent.
            entryAdapter.updateData(entries)
        }
    }

    private fun setupClickListeners() {
        btnToolbarBack.setOnClickListener {
            finish()
        }
    }
}
