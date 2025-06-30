package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class TransferDataActivity : AppCompatActivity() {

    private lateinit var btnToolbarBack: ImageButton

    private lateinit var btnTransferBluetooth: LinearLayout
    private lateinit var btnTransferEmail: LinearLayout


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transfer_data)

        btnToolbarBack = findViewById(R.id.btn_toolbar_back)

        btnTransferBluetooth = findViewById(R.id.btn_transfer_bluetooth)
        btnTransferEmail = findViewById(R.id.btn_transfer_email)

        setupClickListeners()
    }

    private fun setupClickListeners() {

        btnToolbarBack.setOnClickListener {
            finish() // Just close the activity
        }

        btnTransferBluetooth.setOnClickListener {
            // TODO: Launch BluetoothTransferSubActivity (will create next)
            //Toast.makeText(this, "Bluetooth Transfer Coming Soon!", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, BluetoothTransferSubActivity::class.java)
            startActivity(intent)
        }

        btnTransferEmail.setOnClickListener {
            Toast.makeText(this, "Email Transfer Coming Soon!", Toast.LENGTH_SHORT).show()
        }
    }
}