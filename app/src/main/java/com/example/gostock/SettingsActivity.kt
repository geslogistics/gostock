package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.os.Bundle
import android.widget.ImageButton
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    private lateinit var etMaxBatchSize: EditText
    private lateinit var etMaxBatchTime: EditText
    private lateinit var btnToolbarSave: ImageButton
    private lateinit var btnToolbarBack: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etMaxBatchSize = findViewById(R.id.et_max_batch_size)
        etMaxBatchTime = findViewById(R.id.et_max_batch_time)
        btnToolbarSave = findViewById(R.id.btn_toolbar_save)
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)

        loadSettings() // Load current settings on creation
        setupClickListeners()
    }

    private fun loadSettings() {
        etMaxBatchSize.setText(AppSettings.maxBatchSize.toString())
        etMaxBatchTime.setText(AppSettings.maxBatchTime.toString())
    }

    private fun setupClickListeners() {
        btnToolbarSave.setOnClickListener {
            saveSettings()
        }
        btnToolbarBack.setOnClickListener {
            finish() // Go back without saving
        }
    }

    private fun saveSettings() {
        val maxBatchSizeStr = etMaxBatchSize.text.toString()
        val maxBatchTimeStr = etMaxBatchTime.text.toString()

        // Validate input
        val newMaxBatchSize = maxBatchSizeStr.toIntOrNull()
        val newMaxBatchTime = maxBatchTimeStr.toIntOrNull()

        if (newMaxBatchSize == null || newMaxBatchSize < 0) { // Allow 0, only disallow negative
            Toast.makeText(this, "Max Batch Size must be a non-negative number.", Toast.LENGTH_SHORT).show()
            return
        }
        if (newMaxBatchTime == null || newMaxBatchTime < 0) { // Allow 0, only disallow negative
            Toast.makeText(this, "Max Batch Time must be a non-negative number.", Toast.LENGTH_SHORT).show()
            return
        }

        // Save settings using AppSettings object
        AppSettings.maxBatchSize = newMaxBatchSize
        AppSettings.maxBatchTime = newMaxBatchTime

        Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK) // Indicate success
        finish()
    }
}