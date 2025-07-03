package com.example.gostock

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.*
import kotlin.concurrent.thread

@SuppressLint("MissingPermission")
class BluetoothSettingsTransferActivity : AppCompatActivity() {

    // UI elements
    private lateinit var btnToolbarBack: ImageButton
    private lateinit var tvTransferStatus: TextView
    private lateinit var btnEnableBluetooth: Button
    private lateinit var btnMakeDiscoverable: Button
    private lateinit var btnScanDevices: Button
    private lateinit var lvPairedDevices: ListView
    private lateinit var lvDiscoveredDevices: ListView
    private lateinit var btnSendData: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressPercent: TextView
    private lateinit var btnMakeDiscoverableDivider: View

    // Bluetooth components
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var pairedDevicesArrayAdapter: ArrayAdapter<String>
    private val pairedDevicesList = mutableListOf<BluetoothDevice>()
    private lateinit var discoveredDevicesArrayAdapter: ArrayAdapter<String>
    private val discoveredDevicesList = mutableListOf<BluetoothDevice>()
    private var connectedSocket: BluetoothSocket? = null
    private var serverThread: Thread? = null

    // Constants
    private companion object {
        private val MY_UUID: UUID = UUID.fromString("8cc7117b-eca7-4c31-820f-26ed27198bb3") // Unique UUID for settings transfer
        private const val APP_NAME = "GoStockSettingsTransfer"
        private const val TAG = "BluetoothSettings"
    }

    // ActivityResultLaunchers
    private val requestBluetoothPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            checkBluetoothState()
        } else {
            showPermissionsDeniedDialog()
        }
    }
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            checkBluetoothState()
        }
    }
    private val requestDiscoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode > 0) {
            startServer()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_settings_transfer)
        initUI()
        initBluetooth()
        setupClickListeners()
        requestAllPermissions()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: IllegalArgumentException) {
            Log.e(TAG, "Receiver was not registered or already unregistered.", e)
        }
        serverThread?.interrupt()
        connectedSocket?.close()
    }

    private fun initUI() {
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)
        tvTransferStatus = findViewById(R.id.tv_transfer_status_sub)
        btnEnableBluetooth = findViewById(R.id.btn_enable_bluetooth_sub)
        btnMakeDiscoverable = findViewById(R.id.btn_make_discoverable_sub)
        btnMakeDiscoverableDivider = findViewById(R.id.btn_make_discoverable_sub_divider)
        btnScanDevices = findViewById(R.id.btn_scan_devices_sub)
        lvPairedDevices = findViewById(R.id.lv_paired_devices_sub)
        lvDiscoveredDevices = findViewById(R.id.lv_discovered_devices_sub)
        btnSendData = findViewById(R.id.btn_send_data_sub)
        progressBar = findViewById(R.id.progress_bar_sub)
        tvProgressPercent = findViewById(R.id.tv_progress_percent)
    }

    private fun initBluetooth() {
        val bluetoothManager: BluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter
        pairedDevicesArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        lvPairedDevices.adapter = pairedDevicesArrayAdapter
        discoveredDevicesArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        lvDiscoveredDevices.adapter = discoveredDevicesArrayAdapter
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        registerReceiver(bluetoothReceiver, filter)
    }

    private fun setupClickListeners() {
        btnToolbarBack.setOnClickListener { finish() }
        btnEnableBluetooth.setOnClickListener {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }
        btnMakeDiscoverable.setOnClickListener {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
            }
            requestDiscoverableLauncher.launch(discoverableIntent)
        }
        btnScanDevices.setOnClickListener { startDiscovery() }
        lvPairedDevices.setOnItemClickListener { _, _, pos, _ -> connectToDevice(pairedDevicesList[pos]) }
        lvDiscoveredDevices.setOnItemClickListener { _, _, pos, _ ->
            val device = discoveredDevicesList[pos]
            if (device.bondState != BluetoothDevice.BOND_BONDED) {
                device.createBond()
            } else {
                connectToDevice(device)
            }
        }
        btnSendData.setOnClickListener {
            connectedSocket?.let { socket -> sendData(socket) }
                ?: Toast.makeText(this, "Not connected to any device.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startServer() {
        updateUiForListening()
        serverThread = thread(start = true) {
            try {
                val serverSocket: BluetoothServerSocket? = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, MY_UUID)
                val socket = serverSocket?.accept()
                serverSocket?.close()
                socket?.let { manageConnectedSocket(it, isServer = true) }
            } catch (e: IOException) {
                Log.e(TAG, "Server thread error", e)
                runOnUiThread { updateUiForReadyState("❗  Failed to listen") }
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        bluetoothAdapter?.cancelDiscovery()
        updateUiForConnecting(device.name)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val socket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID)
                socket.connect()
                manageConnectedSocket(socket, isServer = false)
            } catch (e: IOException) {
                Log.e(TAG, "Client connection failed", e)
                withContext(Dispatchers.Main) { updateUiForReadyState("❗  Connection failed") }
            }
        }
    }

    private fun manageConnectedSocket(socket: BluetoothSocket, isServer: Boolean) {
        connectedSocket = socket
        runOnUiThread {
            updateUiForConnected(socket.remoteDevice.name)
            if (isServer) {
                receiveData(socket)
            }
        }
    }

    private fun sendData(socket: BluetoothSocket) {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { updateUiForTransfer("Sending settings...") }
            try {
                val settingsData = AppSettingsData(
                    maxBatchSize = AppSettings.maxBatchSize,
                    maxBatchTime = AppSettings.maxBatchTime,
                    enableZebraDevice = AppSettings.enableZebraDevice,
                    acceptedLocationFormats = AppSettings.acceptedLocationFormats,
                    locationRequired = AppSettings.locationRequired,
                    locationEditable = AppSettings.locationEditable,
                    acceptedSkuFormats = AppSettings.acceptedSkuFormats,
                    skuRequired = AppSettings.skuRequired,
                    skuEditable = AppSettings.skuEditable
                )
                val jsonString = Gson().toJson(settingsData)
                DataOutputStream(socket.outputStream).use { it.writeUTF(jsonString) }
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BluetoothSettingsTransferActivity, "✅ Settings sent successfully!", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) { updateUiForReadyState("❗  Send failed") }
            } finally {
                try { socket.close() } catch (e: IOException) { Log.e(TAG, "Could not close the client socket", e) }
            }
        }
    }

    private fun receiveData(socket: BluetoothSocket) {
        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { updateUiForTransfer("\uD83D\uDD35 Receiving settings...") }
            try {
                val jsonString = DataInputStream(socket.inputStream).readUTF()
                val receivedSettings = Gson().fromJson(jsonString, AppSettingsData::class.java)

                // Apply received settings
                AppSettings.maxBatchSize = receivedSettings.maxBatchSize
                AppSettings.maxBatchTime = receivedSettings.maxBatchTime
                AppSettings.enableZebraDevice = receivedSettings.enableZebraDevice
                AppSettings.acceptedLocationFormats = receivedSettings.acceptedLocationFormats
                AppSettings.locationRequired = receivedSettings.locationRequired
                AppSettings.locationEditable = receivedSettings.locationEditable
                AppSettings.acceptedSkuFormats = receivedSettings.acceptedSkuFormats
                AppSettings.skuRequired = receivedSettings.skuRequired
                AppSettings.skuEditable = receivedSettings.skuEditable

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@BluetoothSettingsTransferActivity, "✅ Settings received and applied!", Toast.LENGTH_LONG).show()
                    finish()
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) { updateUiForReadyState("❗  Receive failed") }
            } finally {
                try { socket.close() } catch (e: IOException) { Log.e(TAG, "Could not close the client socket", e) }
            }
        }
    }

    // --- UI and Permission Helpers ---
    private fun updateUiForTransfer(status: String) {
        tvTransferStatus.text = status
        progressBar.visibility = View.VISIBLE
        tvProgressPercent.visibility = View.VISIBLE
        listOf(btnSendData, btnScanDevices, btnMakeDiscoverable, lvPairedDevices, lvDiscoveredDevices, btnToolbarBack).forEach { it.isEnabled = false }
        updateProgress(0)
    }

    private fun updateUiForReadyState(status: String = "⚪  Ready") {
        tvTransferStatus.text = status
        progressBar.visibility = View.GONE
        tvProgressPercent.visibility = View.GONE
        btnSendData.isEnabled = false
        btnScanDevices.isEnabled = true
        btnMakeDiscoverable.isEnabled = true
        listOf(lvPairedDevices, lvDiscoveredDevices, btnToolbarBack).forEach { it.isEnabled = true }
    }

    private fun updateUiForConnected(deviceName: String) {
        tvTransferStatus.text = "✅  Connected to $deviceName"
        progressBar.visibility = View.GONE
        tvProgressPercent.visibility = View.GONE
        btnSendData.isEnabled = true
        btnScanDevices.isEnabled = false
        btnMakeDiscoverable.isEnabled = false
    }

    private fun updateUiForListening() {
        runOnUiThread {
            tvTransferStatus.text = "👂  Listening for connections..."
            btnScanDevices.isEnabled = false
            btnMakeDiscoverable.isEnabled = false
        }
    }

    private fun updateUiForConnecting(deviceName: String) {
        tvTransferStatus.text = "⌛  Connecting to $deviceName..."
        btnScanDevices.isEnabled = false
        btnMakeDiscoverable.isEnabled = false
    }

    private fun requestAllPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filterNot { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestBluetoothPermissionsLauncher.launch(missing.toTypedArray())
        } else {
            checkBluetoothState()
        }
    }

    private fun checkBluetoothState() {
        if (bluetoothAdapter == null) {
            updateUiForReadyState("❗  Bluetooth not supported")
            listOf(btnMakeDiscoverable, btnScanDevices, btnSendData, btnEnableBluetooth).forEach { it.visibility = View.GONE }
            return
        }
        if (bluetoothAdapter?.isEnabled == true) {
            updateUiForReadyState()
            listPairedDevices()
            btnEnableBluetooth.visibility = View.GONE
            btnMakeDiscoverable.visibility = View.VISIBLE
            btnScanDevices.visibility = View.VISIBLE
        } else {
            btnEnableBluetooth.visibility = View.VISIBLE
            listOf(btnMakeDiscoverable, btnScanDevices, btnSendData).forEach { it.visibility = View.GONE }
            updateUiForReadyState("❗  Bluetooth is disabled")
        }
    }

    private fun listPairedDevices() {
        pairedDevicesArrayAdapter.clear()
        pairedDevicesList.clear()
        bluetoothAdapter?.bondedDevices?.forEach { device ->
            pairedDevicesList.add(device)
            pairedDevicesArrayAdapter.add("${device.name}\n${device.address}")
        }
        pairedDevicesArrayAdapter.notifyDataSetChanged()
    }

    private fun startDiscovery() {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        if (!locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER)) {
            AlertDialog.Builder(this)
                .setTitle("Location Services Required")
                .setMessage("For Bluetooth to discover nearby devices, Android requires that Location Services are enabled.")
                .setPositiveButton("Open Settings") { _, _ -> startActivity(Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                .setNegativeButton("Cancel", null).show()
            return
        }
        bluetoothAdapter?.startDiscovery()
    }

    private fun showPermissionsDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permissions Required")
            .setMessage("This feature needs Bluetooth and Location permissions to work. Please grant them in the app settings.")
            .setPositiveButton("Go to Settings") { _, _ ->
                val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                val uri = android.net.Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
            }
            .setNegativeButton("Cancel") { _, _ -> finish() }
            .setCancelable(false)
            .show()
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    device?.let {
                        if (it !in discoveredDevicesList && it.name != null) {
                            discoveredDevicesList.add(it)
                            discoveredDevicesArrayAdapter.add("${it.name}\n${it.address}")
                            discoveredDevicesArrayAdapter.notifyDataSetChanged()
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> tvTransferStatus.text = "🔎  Scanning..."
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> tvTransferStatus.text = "⚪  Ready"
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device: BluetoothDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    }
                    if (device?.bondState == BluetoothDevice.BOND_BONDED) {
                        connectToDevice(device)
                    }
                }
            }
        }
    }
    private fun updateProgress(progress: Int) {
        // This is not strictly needed for settings transfer as it's a single string,
        // but kept for UI consistency if you ever send larger settings files.
        progressBar.progress = progress
        tvProgressPercent.text = "$progress%"
    }
    private fun performLogout() {
        (application as GoStockApp).clearLoginSession()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
}
