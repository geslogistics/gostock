package com.example.gostock // Your package name

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
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ImageButton
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.snackbar.Snackbar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

// Corrected imports to use your package structure
import com.example.gostock.StockEntry
import com.example.gostock.FileHandler


@SuppressLint("MissingPermission") // We handle permissions explicitly with our checks
class BluetoothTransferSubActivity : AppCompatActivity() {

    private fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_LONG) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, duration)

        // Make Snackbar text multiline (works up to a certain point before system truncates)
        val snackbarTextView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        snackbarTextView.maxLines = 5 // Allow up to 5 lines (adjust as needed)
        snackbarTextView.ellipsize = null // Remove ellipsis if text exceeds maxLines

        snackbar.show()
    }

    // --- UI elements declarations ---
    private lateinit var btnToolbarBack: ImageButton
    private lateinit var tvTransferStatus: TextView
    private lateinit var btnEnableBluetooth: Button
    private lateinit var btnMakeDiscoverable: Button
    private lateinit var btnMakeDiscoverableDivider: View
    private lateinit var btnScanDevices: Button
    private lateinit var lvPairedDevices: ListView
    private lateinit var lvDiscoveredDevices: ListView
    private lateinit var btnSendData: Button
    private lateinit var progressBar: ProgressBar

    // --- Bluetooth core components declarations ---
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var pairedDevicesArrayAdapter: ArrayAdapter<String>
    private val pairedDevicesList = mutableListOf<BluetoothDevice>()
    private lateinit var discoveredDevicesArrayAdapter: ArrayAdapter<String>
    private val discoveredDevicesList = mutableListOf<BluetoothDevice>()

    // --- For Bluetooth communication threads ---
    private var connectedDevice: BluetoothDevice? = null
    private var bluetoothService: ConnectedThread? = null
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null

    // --- Constants ---
    private companion object {
        private val MY_UUID: UUID = UUID.fromString("8cc7117b-eca7-4c31-820f-26ed27198bb0")
        private const val APP_NAME = "GoStockAppTransfer"
        private const val TAG = "BluetoothTransfer"
        const val STOCK_DATA_FILENAME = "stock_data.json"
    }

    private enum class BluetoothState {
        PERMISSIONS_DENIED, DISABLED, ENABLED, DISCOVERING, LISTENING, CONNECTING, CONNECTED
    }

    // --- ActivityResultLaunchers ---
    private val requestBluetoothPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            Toast.makeText(this, "Bluetooth permissions granted.", Toast.LENGTH_SHORT).show()
            checkBluetoothState()
        } else {
            // User denied at least one permission. Show a helpful dialog.
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("Permissions Required")
                .setMessage("This feature cannot work without Bluetooth and Location permissions. Location is required by Android to scan for nearby devices. Please grant the necessary permissions from the app settings.")
                .setPositiveButton("Go to Settings") { _, _ ->
                    // Create an intent that opens this specific app's settings page
                    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                    val uri = android.net.Uri.fromParts("package", packageName, null)
                    intent.data = uri
                    startActivity(intent)
                }
                .setNegativeButton("Cancel", null)
                .show()

            updateUiForBluetoothState(BluetoothState.PERMISSIONS_DENIED)
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(this, "Bluetooth enabled.", Toast.LENGTH_SHORT).show()
            checkBluetoothState()
        } else {
            Toast.makeText(this, "Bluetooth not enabled.", Toast.LENGTH_SHORT).show()
            updateUiForBluetoothState(BluetoothState.DISABLED)
        }
    }

    private val requestDiscoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode > 0) {
            Toast.makeText(this, "Device discoverable for ${result.resultCode} seconds.", Toast.LENGTH_SHORT).show()
            startServer()
        } else {
            Toast.makeText(this, "Device not made discoverable.", Toast.LENGTH_LONG).show()
            checkBluetoothState()
        }
    }

    // --- BroadcastReceiver ---
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (it !in discoveredDevicesList && it.name != null) {
                            discoveredDevicesList.add(it)
                            discoveredDevicesArrayAdapter.add("${it.name}\n${it.address}")
                            discoveredDevicesArrayAdapter.notifyDataSetChanged()
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    updateUiForBluetoothState(BluetoothState.DISCOVERING)
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    updateUiForBluetoothState(BluetoothState.ENABLED)
                    if (discoveredDevicesList.isEmpty()) {
                        Toast.makeText(context, "No new devices found.", Toast.LENGTH_SHORT).show()
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    listPairedDevices()
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR) == BluetoothDevice.BOND_BONDED) {
                        Toast.makeText(context, "Paired with ${device?.name}", Toast.LENGTH_SHORT).show()
                        device?.let { connectDevice(it) }
                    }
                }
            }
        }
    }

    // --- INNER THREAD CLASSES ---
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        @Volatile var isConnected: Boolean = true
        override fun run() {
            val buffer = ByteArray(4096)
            var numBytes: Int
            while (isConnected) {
                try {
                    numBytes = mmInStream.read(buffer)
                    val receivedData = String(buffer, 0, numBytes)
                    runOnUiThread { processReceivedData(receivedData) }
                } catch (e: IOException) {
                    connectionLost()
                    break
                }
            }
        }
        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
            } catch (e: IOException) {
                connectionLost()
            }
        }
        fun cancel() {
            try {
                isConnected = false
                mmSocket.close()
            } catch (e: IOException) { Log.e(TAG, "Could not close the connect socket", e) }
        }
    }

    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            if (checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                // CHANGE: Use the 'insecure' method for better compatibility
                bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, MY_UUID)
            } else null
        }

        override fun run() {
            var socket: BluetoothSocket?
            while (true) {
                try {
                    runOnUiThread { updateUiForBluetoothState(BluetoothState.LISTENING) }
                    socket = mmServerSocket?.accept()
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's accept() failed or was cancelled.", e)
                    break
                }

                if (socket != null) {
                    Log.d(TAG, "Connection accepted.")
                    manageConnectedSocket(socket!!, socket!!.remoteDevice)
                    mmServerSocket?.close()
                    break
                }
            }
        }

        fun cancel() {
            try {
                mmServerSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the server socket", e)
            }
        }
    }

    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            if (checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) {
                // CHANGE: Use the 'insecure' method for better compatibility
                device.createInsecureRfcommSocketToServiceRecord(MY_UUID)
            } else null
        }

        override fun run() {
            bluetoothAdapter?.cancelDiscovery()
            mmSocket?.let { socket ->
                try {
                    runOnUiThread { updateUiForBluetoothState(BluetoothState.CONNECTING, device.name) }
                    socket.connect()
                    manageConnectedSocket(socket, device)
                } catch (e: IOException) {
                    connectionFailed()
                    try { socket.close() } catch (ex: IOException) {}
                }
            } ?: connectionFailed()
        }

        fun cancel() {
            try {
                mmSocket?.close()
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }


    // --- Activity Lifecycle ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_transfer_sub)

        // Initialize UI elements using your layout's IDs
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

        setupClickListeners()
        requestAllPermissions()

        val loggedInUser = GoStockApp.loggedInUser
        if (loggedInUser != null) {
            if (loggedInUser.role == UserRole.STOCKTAKER) {
                btnMakeDiscoverable.visibility = View.GONE
                btnMakeDiscoverableDivider.visibility = View.GONE
            } else {
                btnMakeDiscoverable.visibility = View.VISIBLE
                btnMakeDiscoverableDivider.visibility = View.VISIBLE
            }
        } else {
            showSnackbar("User not logged in. Redirecting to login.", Snackbar.LENGTH_LONG)
            performLogout()
        }
    }
    private fun performLogout() {
        (application as GoStockApp).clearLoginSession()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        showSnackbar( "Logged out successfully!", Snackbar.LENGTH_SHORT)
    }

//    override fun onResume() {
//        super.onResume()
//        requestAllPermissions()
//    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
        bluetoothService?.cancel()
        acceptThread?.cancel()
        connectThread?.cancel()
    }


    // --- Helper Functions ---

    private fun setupClickListeners() {
        btnToolbarBack.setOnClickListener { finish() } // Handle back button press

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

        lvPairedDevices.setOnItemClickListener { _, _, position, _ ->
            bluetoothAdapter?.cancelDiscovery()
            connectDevice(pairedDevicesList[position])
        }

        lvDiscoveredDevices.setOnItemClickListener { _, _, position, _ ->
            bluetoothAdapter?.cancelDiscovery()
            val device = discoveredDevicesList[position]
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                connectDevice(device)
            } else {
                Toast.makeText(this, "Pairing with ${device.name}...", Toast.LENGTH_SHORT).show()
                device.createBond()
            }
        }

        btnSendData.setOnClickListener {
            if (bluetoothService != null && bluetoothService!!.isConnected) {
                val fileHandler = FileHandler(this, STOCK_DATA_FILENAME)
                val jsonString = fileHandler.readJsonFromFile()
                if (!jsonString.isNullOrEmpty()) {
                    bluetoothService?.write(jsonString.toByteArray())
                    Toast.makeText(this, "Data sent!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No data to send.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Not connected to a device.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAllPermissions() {
        Log.d(TAG, "--- Checking Permissions for SDK version ${Build.VERSION.SDK_INT} ---")

        val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }

        // This block will log the status of each permission individually
        val missingPermissions = permissionsToRequest.filter { permission ->
            val hasPermission = checkPermission(permission)
            Log.d(TAG, "Verifying Permission: $permission, Granted: $hasPermission")
            !hasPermission
        }
        Log.d(TAG, "----------------------------------------------------")


        if (missingPermissions.isNotEmpty()) {
            Log.w(TAG, "Found missing permissions. Requesting: ${missingPermissions.joinToString()}")
            requestBluetoothPermissionsLauncher.launch(missingPermissions.toTypedArray())
        } else {
            Log.d(TAG, "All necessary permissions are already granted.")
            checkBluetoothState()
        }
    }

    private fun checkBluetoothState() {
        if (bluetoothAdapter == null) {
            updateUiForBluetoothState(BluetoothState.PERMISSIONS_DENIED, "Bluetooth not supported")
            return
        }
        if (bluetoothAdapter?.isEnabled == true) {
            updateUiForBluetoothState(BluetoothState.ENABLED)
            listPairedDevices()
        } else {
            updateUiForBluetoothState(BluetoothState.DISABLED)
        }
    }

    private fun updateUiForBluetoothState(state: BluetoothState, deviceName: String? = null) {
        progressBar.visibility = View.GONE
        btnEnableBluetooth.visibility = View.GONE
        btnScanDevices.isEnabled = false
        btnMakeDiscoverable.isEnabled = false
        btnSendData.isEnabled = false

        when (state) {
            BluetoothState.PERMISSIONS_DENIED -> tvTransferStatus.text = deviceName ?: "Status: Bluetooth permissions required"
            BluetoothState.DISABLED -> {
                tvTransferStatus.text = "Status: Bluetooth is disabled"
                btnEnableBluetooth.visibility = View.VISIBLE
            }
            BluetoothState.ENABLED -> {
                tvTransferStatus.text = "Status: Ready"
                btnScanDevices.isEnabled = true
                btnMakeDiscoverable.isEnabled = true
            }
            BluetoothState.DISCOVERING -> {
                tvTransferStatus.text = "Status: Scanning for devices..."
                progressBar.visibility = View.VISIBLE
            }
            BluetoothState.LISTENING -> {
                tvTransferStatus.text = "Status: Listening for connections..."
                progressBar.visibility = View.VISIBLE
            }
            BluetoothState.CONNECTING -> {
                tvTransferStatus.text = "Status: Connecting to $deviceName..."
                progressBar.visibility = View.VISIBLE
            }
            BluetoothState.CONNECTED -> {
                tvTransferStatus.text = "Status: Connected to $deviceName"
                btnSendData.isEnabled = true
            }
        }
    }

    private fun listPairedDevices() {
        if (!checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) return
        pairedDevicesArrayAdapter.clear()
        pairedDevicesList.clear()
        bluetoothAdapter?.bondedDevices?.forEach { device ->
            pairedDevicesList.add(device)
            pairedDevicesArrayAdapter.add("${device.name}\n${device.address}")
        }
        pairedDevicesArrayAdapter.notifyDataSetChanged()
    }

    private fun startDiscovery() {
        if (!checkPermission(Manifest.permission.BLUETOOTH_SCAN)) return
        if (bluetoothAdapter?.isDiscovering == true) bluetoothAdapter?.cancelDiscovery()
        discoveredDevicesList.clear()
        discoveredDevicesArrayAdapter.clear()
        discoveredDevicesArrayAdapter.notifyDataSetChanged()
        bluetoothAdapter?.startDiscovery()
    }

    private fun connectDevice(device: BluetoothDevice) {
        connectThread = ConnectThread(device)
        connectThread?.start()
    }

    private fun startServer() {
        acceptThread = AcceptThread()
        acceptThread?.start()
    }

    private fun manageConnectedSocket(socket: BluetoothSocket, device: BluetoothDevice) {
        // When a connection is made, we should stop listening for more connections. This is correct.
        acceptThread?.cancel()

        // The ConnectThread's job is done, but we should NOT cancel it,
        // because its cancel() method closes the socket we need.
        // The thread will finish its run() method and terminate on its own.
        // connectThread?.cancel()  // <--- THIS IS THE BUG. WE REMOVE THIS LINE.

        // Create and start the new thread for data transmission with the live socket.
        bluetoothService = ConnectedThread(socket)
        bluetoothService?.start()
        connectedDevice = device

        // Update the UI to show we are connected.
        runOnUiThread {
            updateUiForBluetoothState(BluetoothState.CONNECTED, device.name)
        }
    }

    private fun connectionFailed() {
        runOnUiThread {
            Toast.makeText(this, "Connection failed.", Toast.LENGTH_SHORT).show()
            checkBluetoothState()
        }
    }

    private fun connectionLost() {
        runOnUiThread {
            Toast.makeText(this, "Connection lost.", Toast.LENGTH_SHORT).show()
            checkBluetoothState()
        }
    }

    private fun processReceivedData(jsonData: String) {
        val listType = object : TypeToken<List<StockEntry>>() {}.type
        try {
            val receivedEntries: List<StockEntry> = Gson().fromJson(jsonData, listType)
            if (receivedEntries.isNotEmpty()) {
                val fileHandler = FileHandler(this, STOCK_DATA_FILENAME)
                fileHandler.addMultipleStockEntries(receivedEntries)
                Toast.makeText(this, "Imported ${receivedEntries.size} records!", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Received invalid data.", Toast.LENGTH_LONG).show()
            Log.e(TAG, "Error processing received data: ${e.message}", e)
        }
    }
}