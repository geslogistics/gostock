package com.example.gostock

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.reflect.TypeToken

class RecordListActivity : AppCompatActivity() {

    private lateinit var btnToolbarBack: ImageButton
    private lateinit var recyclerView: RecyclerView
    private lateinit var tvNoRecords: TextView

    // --- UPDATED: Use the new generic JsonFileHandler ---
    private lateinit var stockFileHandler: JsonFileHandler<StockEntry>

    private lateinit var entryAdapter: EntryAdapter
    private var records: MutableList<StockEntry> = mutableListOf()

    companion object {
        const val EXTRA_STOCK_ENTRY = "extra_stock_entry"
    }

    private val editRecordLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            loadAndDisplayRecords()
            Toast.makeText(this, "Record updated successfully!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_record_list)

        initViews()

        // --- UPDATED: Initialize the generic handler with the correct type ---
        val stockListType = object : TypeToken<MutableList<StockEntry>>() {}
        stockFileHandler = JsonFileHandler(this, "stock_data.json", stockListType)

        setupRecyclerView()
        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        loadAndDisplayRecords()
    }

    private fun initViews() {
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)
        recyclerView = findViewById(R.id.recyclerView_records)
        tvNoRecords = findViewById(R.id.tv_no_records)
    }

    private fun setupRecyclerView() {
        entryAdapter = EntryAdapter(records) { clickedEntry ->
            val intent = Intent(this, EditRecordActivity::class.java).apply {
                putExtra(EXTRA_STOCK_ENTRY, clickedEntry)
            }
            editRecordLauncher.launch(intent)
        }
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = entryAdapter
    }

    private fun loadAndDisplayRecords() {
        // --- UPDATED: Use the new handler's method name ---
        val loadedEntries = stockFileHandler.loadRecords()

        // Sorting by Long timestamp is now direct and efficient
        val sortedEntries = loadedEntries.sortedByDescending { it.timestamp }

        records.clear()
        records.addAll(sortedEntries)

        if (records.isEmpty()) {
            recyclerView.visibility = View.GONE
            tvNoRecords.visibility = View.VISIBLE
        } else {
            recyclerView.visibility = View.VISIBLE
            tvNoRecords.visibility = View.GONE
            entryAdapter.updateData(records)
        }
    }

    private fun setupClickListeners() {
        btnToolbarBack.setOnClickListener {
            finish()
        }
    }
}
