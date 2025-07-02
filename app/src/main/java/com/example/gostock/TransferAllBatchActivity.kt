package com.example.gostock

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class TransferAllBatchActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transfer_all_batch)

        val btnToolbarBack: ImageButton = findViewById(R.id.btn_toolbar_back)
        val btnTransferBluetooth: LinearLayout = findViewById(R.id.btn_transfer_bluetooth)
        val btnTransferEmail: LinearLayout = findViewById(R.id.btn_transfer_email)

        btnToolbarBack.setOnClickListener { finish() }

        btnTransferBluetooth.setOnClickListener {
            // Launch the new Bluetooth activity for batch transfers
            val intent = Intent(this, BluetoothTransferAllBatchActivity::class.java)
            startActivity(intent)
        }

        btnTransferEmail.setOnClickListener {
            Toast.makeText(this, "Email Transfer Coming Soon!", Toast.LENGTH_SHORT).show()
        }
    }
}