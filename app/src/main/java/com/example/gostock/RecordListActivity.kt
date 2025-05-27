package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.content.Intent
import android.os.Bundle
// import android.util.Log // Log import removed for cleaner output
import android.view.View
import android.widget.TextView
import android.widget.Toast // Still needed for toasts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.result.contract.ActivityResultContracts

class RecordListActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoRecords: TextView
    private lateinit var fileHandler: FileHandler
    private lateinit var entryAdapter: EntryAdapter
    private var records: MutableList<StockEntry> = mutableListOf() // Data source for the adapter

    companion object {
        const val EXTRA_STOCK_ENTRY = "extra_stock_entry"
        const val REQUEST_CODE_EDIT_RECORD = 101
        // private const val TAG = "RecordListActivity" // Log Tag removed
    }

    // Using ActivityResultLauncher for better practice than onActivityResult
    private val editRecordLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadAndDisplayRecords() // Reload list if record was updated or deleted
            Toast.makeText(this, "Record updated successfully!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Record edit cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record_list)

        recyclerView = findViewById(R.id.recyclerView_records)
        tvNoRecords = findViewById(R.id.tv_no_records)
        fileHandler = FileHandler(this) // Initialize FileHandler

        setupRecyclerView()
        // Data will be loaded in onResume to ensure fresh data after returning from EditRecordActivity
    }

    override fun onResume() {
        super.onResume()
        // Load records every time the activity comes to the foreground
        // This ensures the list is always fresh.
        loadAndDisplayRecords()
    }

    private fun setupRecyclerView() {
        // Log.d(TAG, "Setting up RecyclerView.") // Debug log removed
        // Initialize the adapter with the mutable list and a click listener
        // The 'records' list is passed by reference, so changes to it (e.g., in loadAndDisplayRecords)
        // will be reflected IF updateData is called.
        entryAdapter = EntryAdapter(records) { clickedEntry ->
            // Log.d(TAG, "Item clicked: ${clickedEntry.id}") // Debug log removed
            // Handle item click: launch EditRecordActivity
            val intent = Intent(this, EditRecordActivity::class.java).apply {
                putExtra(EXTRA_STOCK_ENTRY, clickedEntry) // Pass the entire StockEntry object
            }
            editRecordLauncher.launch(intent) // Use the launcher to start the activity
        }

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = entryAdapter
        // Log.d(TAG, "RecyclerView adapter and layout manager set.") // Debug log removed
    }

    private fun loadAndDisplayRecords() {
        // Log.d(TAG, "loadAndDisplayRecords() called.") // Debug log removed
        val loadedEntries = fileHandler.loadStockEntries()
        // Log.d(TAG, "FileHandler returned ${loadedEntries.size} entries.") // Debug log removed

        records.clear()
        records.addAll(loadedEntries)

        if (records.isEmpty()) {
            // Log.d(TAG, "Records list is empty. Hiding RecyclerView, showing No Records text.") // Debug log removed
            recyclerView.visibility = View.GONE
            tvNoRecords.visibility = View.VISIBLE
        } else {
            // Log.d(TAG, "Records list has ${records.size} entries. Showing RecyclerView.") // Debug log removed
            recyclerView.visibility = View.VISIBLE
            tvNoRecords.visibility = View.GONE
            // IMPORTANT: Notify the adapter that its underlying data has changed
            entryAdapter.updateData(records) // This calls notifyDataSetChanged internally
        }
    }
}