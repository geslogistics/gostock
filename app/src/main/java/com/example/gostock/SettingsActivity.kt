package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Switch
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale
import android.text.TextUtils


class SettingsActivity : AppCompatActivity() {

    private lateinit var etMaxBatchSize: EditText
    private lateinit var etMaxBatchTime: EditText
    private lateinit var btnToolbarSave: ImageButton
    private lateinit var btnToolbarBack: ImageButton
    private lateinit var switchEnableZebraDevice: Switch
    private lateinit var etAcceptedLocationFormats: EditText
    private lateinit var etAcceptedSkuFormats: EditText


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etMaxBatchSize = findViewById(R.id.et_max_batch_size)
        etMaxBatchTime = findViewById(R.id.et_max_batch_time)
        btnToolbarSave = findViewById(R.id.btn_toolbar_save)
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)
        switchEnableZebraDevice = findViewById(R.id.switch_enable_zebra_device)
        etAcceptedLocationFormats = findViewById(R.id.et_accepted_location_formats)
        etAcceptedSkuFormats = findViewById(R.id.et_accepted_sku_formats)




        loadSettings() // Load current settings on creation
        setupClickListeners()
    }

    private fun loadSettings() {
        etMaxBatchSize.setText(AppSettings.maxBatchSize.toString())
        etMaxBatchTime.setText(AppSettings.maxBatchTime.toString())
        switchEnableZebraDevice.isChecked = AppSettings.enableZebraDevice
        etAcceptedLocationFormats.setText(TextUtils.join(", ", AppSettings.acceptedLocationFormats))
        etAcceptedSkuFormats.setText(TextUtils.join(", ", AppSettings.acceptedSkuFormats))
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
        val acceptedLocationFormatsStr = etAcceptedLocationFormats.text.toString().trim()
        val acceptedSkuFormatsStr = etAcceptedSkuFormats.text.toString().trim()


        // Validate input
        val newMaxBatchSize = maxBatchSizeStr.toIntOrNull()
        val newMaxBatchTime = maxBatchTimeStr.toIntOrNull()
        val newEnableZebraDevice = switchEnableZebraDevice.isChecked

        if (newMaxBatchSize == null || newMaxBatchSize < 0) { // Allow 0, only disallow negative
            Toast.makeText(this, "Max Batch Size must be a non-negative number.", Toast.LENGTH_SHORT).show()
            return
        }
        if (newMaxBatchTime == null || newMaxBatchTime < 0) { // Allow 0, only disallow negative
            Toast.makeText(this, "Max Batch Time must be a non-negative number.", Toast.LENGTH_SHORT).show()
            return
        }

        val newAcceptedLocationFormats = if (acceptedLocationFormatsStr.isNotEmpty()) {
            acceptedLocationFormatsStr.split(",").map { it.trim().uppercase(Locale.ROOT) }.toSet()
        } else {
            emptySet()
        }
        val newAcceptedSkuFormats = if (acceptedSkuFormatsStr.isNotEmpty()) {
            acceptedSkuFormatsStr.split(",").map { it.trim().uppercase(Locale.ROOT) }.toSet()
        } else {
            emptySet()
        }

        // Save settings using AppSettings object
        AppSettings.maxBatchSize = newMaxBatchSize
        AppSettings.maxBatchTime = newMaxBatchTime
        AppSettings.enableZebraDevice = newEnableZebraDevice
        AppSettings.acceptedLocationFormats = newAcceptedLocationFormats // SAVE NEW SETTING
        AppSettings.acceptedSkuFormats = newAcceptedSkuFormats // SAVE NEW SETTING


        Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK) // Indicate success
        finish()
    }
}