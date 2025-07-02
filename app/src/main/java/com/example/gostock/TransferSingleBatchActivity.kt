package com.example.gostock

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class TransferSingleBatchActivity : AppCompatActivity() {

    private var batchToTransfer: Batch? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transfer_single_batch)

        // Retrieve the batch object passed from the previous activity
        batchToTransfer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BatchEntryListActivity.EXTRA_BATCH_OBJECT, Batch::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BatchEntryListActivity.EXTRA_BATCH_OBJECT)
        }

        if (batchToTransfer == null) {
            Toast.makeText(this, "Error: No batch selected for transfer.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val btnToolbarBack: ImageButton = findViewById(R.id.btn_toolbar_back)
        val btnTransferBluetooth: LinearLayout = findViewById(R.id.btn_transfer_bluetooth)
        val btnTransferEmail: LinearLayout = findViewById(R.id.btn_transfer_email)

        btnToolbarBack.setOnClickListener { finish() }

        btnTransferBluetooth.setOnClickListener {
            val intent = Intent(this, BluetoothTransferSingleBatchActivity::class.java).apply {
                putExtra(BluetoothTransferSingleBatchActivity.EXTRA_BATCH_TO_TRANSFER, batchToTransfer)
            }
            startActivity(intent)
        }

        btnTransferEmail.setOnClickListener {
            Toast.makeText(this, "Email Transfer Coming Soon!", Toast.LENGTH_SHORT).show()
        }
    }
}
