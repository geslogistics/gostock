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


class SettingsActivity : AppCompatActivity() {

    private lateinit var etMaxBatchSize: EditText
    private lateinit var etMaxBatchTime: EditText
    private lateinit var btnToolbarSave: ImageButton
    private lateinit var btnToolbarBack: ImageButton
    private lateinit var switchEnableZebraDevice: Switch
    private lateinit var btnCheckZebra: Button
    private lateinit var tvZebraStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        etMaxBatchSize = findViewById(R.id.et_max_batch_size)
        etMaxBatchTime = findViewById(R.id.et_max_batch_time)
        btnToolbarSave = findViewById(R.id.btn_toolbar_save)
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)
        switchEnableZebraDevice = findViewById(R.id.switch_enable_zebra_device)
        btnCheckZebra = findViewById(R.id.btn_check_zebra)
        tvZebraStatus = findViewById(R.id.tv_zebra_status)

        loadSettings() // Load current settings on creation
        setupClickListeners()
    }

    private fun loadSettings() {
        etMaxBatchSize.setText(AppSettings.maxBatchSize.toString())
        etMaxBatchTime.setText(AppSettings.maxBatchTime.toString())
        switchEnableZebraDevice.isChecked = AppSettings.enableZebraDevice
    }

    private fun setupClickListeners() {
        btnToolbarSave.setOnClickListener {
            saveSettings()
        }
        btnToolbarBack.setOnClickListener {
            finish() // Go back without saving
        }
        btnCheckZebra.setOnClickListener {
            checkZebra()
        }
    }
    private fun checkZebra() {
        val i = Intent()
        i.setAction("com.symbol.datawedge.api.ACTION")
        i.putExtra("com.symbol.datawedge.api.GET_DATAWEDGE_STATUS", "")
        this.sendBroadcast(i)


        val broadcastReceiverDWStatus: BroadcastReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.hasExtra("com.symbol.datawedge.api.RESULT_GET_DATAWEDGE_STATUS")) {
                    val dwStatus = intent.getStringExtra("com.symbol.datawedge.api.RESULT_GET_DATAWEDGE_STATUS")
                }
            }
        }

        tvZebraStatus.text = broadcastReceiverDWStatus.toString()




    }

    private fun saveSettings() {
        val maxBatchSizeStr = etMaxBatchSize.text.toString()
        val maxBatchTimeStr = etMaxBatchTime.text.toString()

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

        // Save settings using AppSettings object
        AppSettings.maxBatchSize = newMaxBatchSize
        AppSettings.maxBatchTime = newMaxBatchTime
        AppSettings.enableZebraDevice = newEnableZebraDevice

        Toast.makeText(this, "Settings saved successfully!", Toast.LENGTH_SHORT).show()
        setResult(RESULT_OK) // Indicate success
        finish()
    }
}