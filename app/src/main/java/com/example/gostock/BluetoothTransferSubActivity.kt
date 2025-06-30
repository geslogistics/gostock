package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.Manifest
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
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID


class BluetoothTransferSubActivity : AppCompatActivity() {

    // --- UI elements declarations ---
    private lateinit var tvTransferStatus: TextView
    private lateinit var btnEnableBluetooth: Button
    private lateinit var btnMakeDiscoverable: Button
    private lateinit var btnScanDevices: Button
    private lateinit var lvPairedDevices: ListView
    private lateinit var lvDiscoveredDevices: ListView
    private lateinit var btnSendData: Button
    private lateinit var btnReceiveData: Button

    // --- Bluetooth core components declarations ---
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var pairedDevicesArrayAdapter: ArrayAdapter<String>
    private val pairedDevicesList = mutableListOf<String>()
    private lateinit var discoveredDevicesArrayAdapter: ArrayAdapter<String>
    private val discoveredDevicesList = mutableListOf<String>()
    private val discoveredBluetoothDevices = mutableMapOf<String, BluetoothDevice>()

    // --- For Bluetooth communication threads (declared here - definitions appear below) ---
    private var connectedDevice: BluetoothDevice? = null
    private var bluetoothService: ConnectedThread? = null
    private var acceptThread: AcceptThread? = null
    private var connectThread: ConnectThread? = null

    // --- Bluetooth Service UUID & Name ---
    // This UUID MUST be the SAME on both connecting devices.
    private val MY_UUID: UUID = UUID.fromString("8cc7117b-eca7-4c31-820f-26ed27198bb0") // User-specified UUID
    private val APP_NAME = "GoStockAppTransfer" // Name for your Bluetooth service (used for SPP record)

    private val TAG = "TransferDataActivity" // Log Tag for this specific activity


    // --- Request codes for ActivityResultLauncher ---
    companion object {
        private const val REQUEST_CODE_BLUETOOTH_PERMISSIONS = 1
        private const val REQUEST_CODE_ENABLE_BLUETOOTH = 2
        const val STOCK_DATA_FILENAME = "stock_data.json" // Constant for the filename
    }

    // --- ActivityResultLaunchers (Defined as properties of the class) ---
    private val requestBluetoothPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Bluetooth permissions granted.", Toast.LENGTH_SHORT).show()
            checkBluetoothState() // Calls method defined later
        } else {
            Toast.makeText(this, "Bluetooth permissions denied. Cannot use Bluetooth features.", Toast.LENGTH_LONG).show()
            tvTransferStatus.text = "Status: Permissions Denied"
            updateUiForBluetoothState(BluetoothState.PERMISSIONS_DENIED) // Calls method defined later
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth enabled.", Toast.LENGTH_SHORT).show()
            Handler(Looper.getMainLooper()).postDelayed({
                checkBluetoothState() // Calls method defined later
            }, 500)
        } else {
            Toast.makeText(this, "Bluetooth not enabled. Cannot proceed.", Toast.LENGTH_SHORT).show()
            updateUiForBluetoothState(BluetoothState.DISABLED) // Calls method defined later
        }
    }

    private val requestLocationServicesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        checkBluetoothState() // Calls method defined later
    }

    private val requestDiscoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Device discoverable for ${result.resultCode} seconds.", Toast.LENGTH_SHORT).show()
            startServer() // Calls method defined later
        } else {
            Toast.makeText(this, "Device not made discoverable. Cannot receive connections.", Toast.LENGTH_LONG).show()
            updateUiForBluetoothState(BluetoothState.ENABLED_NOT_DISCOVERABLE) // Calls method defined later
        }
    }

    // --- BroadcastReceiver (Defined as a property of the class) ---
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        if (checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) { // Calls method defined later
                            if (it.name != null && !discoveredDevicesList.contains("${it.name}\n${it.address}")) {
                                discoveredDevicesList.add("${it.name}\n${it.address}")
                                discoveredBluetoothDevices[it.address] = it
                                discoveredDevicesArrayAdapter.notifyDataSetChanged()
                                Toast.makeText(context, "Found device: ${it.name}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            if (!discoveredDevicesList.contains("Unknown Device\n${it.address}")) {
                                discoveredDevicesList.add("Unknown Device\n${it.address}")
                                discoveredBluetoothDevices[it.address] = it
                                discoveredDevicesArrayAdapter.notifyDataSetChanged()
                                Toast.makeText(context, "Found device: ${it.address}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    Toast.makeText(context, "Scanning started...", Toast.LENGTH_SHORT).show()
                    discoveredDevicesList.clear()
                    discoveredBluetoothDevices.clear()
                    discoveredDevicesArrayAdapter.notifyDataSetChanged()
                    tvTransferStatus.text = "Status: Scanning..."
                    btnScanDevices.isEnabled = false
                    btnMakeDiscoverable.isEnabled = false
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    Toast.makeText(context, "Scanning finished.", Toast.LENGTH_SHORT).show()
                    tvTransferStatus.text = "Status: Enabled"
                    btnScanDevices.isEnabled = true
                    btnMakeDiscoverable.isEnabled = true
                    if (discoveredDevicesList.isEmpty()) {
                        Toast.makeText(context, "No new devices found.", Toast.LENGTH_SHORT).show()
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    when (intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.ERROR)) {
                        BluetoothDevice.BOND_BONDED -> {
                            Toast.makeText(context, "Paired with ${device?.name ?: device?.address}", Toast.LENGTH_SHORT).show()
                            listPairedDevices() // Calls method defined later
                            device?.let {
                                if (checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) { // Calls method defined later
                                    connectDevice(it) // Calls method defined later
                                }
                            }
                        }
                        BluetoothDevice.BOND_BONDING -> {
                            Toast.makeText(context, "Pairing with ${device?.name ?: device?.address}...", Toast.LENGTH_SHORT).show()
                        }
                        BluetoothDevice.BOND_NONE -> {
                            Toast.makeText(context, "Unpaired with ${device?.name ?: device?.address}", Toast.LENGTH_SHORT).show()
                            listPairedDevices() // Calls method defined later
                        }
                    }
                }
            }
        }
    }


    // --- INNER THREAD CLASSES (Defined as inner classes of the Activity, before helper methods) ---

    /** Handles sending and receiving data on an active connection. */
    private inner class ConnectedThread(private val mmSocket: BluetoothSocket) : Thread() {
        private val mmInStream: InputStream = mmSocket.inputStream
        private val mmOutStream: OutputStream = mmSocket.outputStream
        private val mmBuffer: ByteArray = ByteArray(1024)

        @Volatile var isConnected: Boolean = true // Use @Volatile for thread safety

        override fun run() {
            var numBytes: Int // Bytes returned from read()

            // Keep listening to the InputStream until an exception occurs (connection lost).
            while (isConnected) {
                try {
                    numBytes = mmInStream.read(mmBuffer)
                    val receivedBytes = mmBuffer.copyOfRange(0, numBytes) // Copy only the actual bytes read
                    val receivedData = String(receivedBytes) // Convert bytes to String (JSON)
                    Log.d(TAG, "Received: $receivedData")

                    runOnUiThread {
                        processReceivedData(receivedData) // Calls method of outer class
                    }

                } catch (e: IOException) {
                    Log.d(TAG, "Input stream was disconnected", e)
                    isConnected = false // Mark as disconnected
                    connectionLost() // Calls method of outer class
                    break // Exit the listening loop
                }
            }
        }

        /** Writes bytes to the OutputStream (sends data to the remote device). */
        fun write(bytes: ByteArray) {
            try {
                mmOutStream.write(bytes)
                Log.d(TAG, "Sent: ${String(bytes)}")
            } catch (e: IOException) {
                Log.e(TAG, "Error during write", e)
                connectionLost() // Calls method of outer class
            }
        }

        /** Call this method from the main activity to shut down the connection. */
        fun cancel() {
            try {
                mmSocket.close()
                isConnected = false // Mark as disconnected
                Log.d(TAG, "ConnectedThread: Socket closed.")
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the connect socket", e)
            }
        }
    }

    /** Server thread for listening for incoming connections. */
    private inner class AcceptThread : Thread() {
        private val mmServerSocket: BluetoothServerSocket? by lazy(LazyThreadSafetyMode.NONE) {
            if (checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) { // Calls method of outer class
                try {
                    bluetoothAdapter?.listenUsingRfcommWithServiceRecord(APP_NAME, MY_UUID)
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's listen() method failed", e)
                    connectionFailed() // Calls method of outer class
                    null
                }
            } else {
                Log.e(TAG, "BLUETOOTH_CONNECT permission not granted for listenUsingRfcommWithServiceRecord")
                connectionFailed() // Calls method of outer class
                null
            }
        }

        override fun run() {
            var socket: BluetoothSocket? = null
            // Keep listening until exception occurs or a socket is returned.
            while (true) {
                try {
                    Log.d(TAG, "AcceptThread: Waiting for incoming connection...")
                    socket = mmServerSocket?.accept() // This is a blocking call
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's accept() method failed", e)
                    break // Exit loop on exception (server socket closed or error)
                }

                // If a connection was accepted
                socket?.also {
                    Log.d(TAG, "AcceptThread: Connection accepted from ${it.remoteDevice.name ?: it.remoteDevice.address}.")
                    manageConnectedSocket(it, it.remoteDevice) // Calls method of outer class
                    return // Exit this AcceptThread after managing the connection
                }
            }
            // If the loop breaks, try to restart the server after a short delay,
            // but only if we are not already connected to a device.
            if (connectedDevice == null || bluetoothService == null || !bluetoothService!!.isConnected) {
                Handler(Looper.getMainLooper()).postDelayed({ startServer() }, 1000) // Calls method of outer class
            }
        }

        fun cancel() {
            try {
                mmServerSocket?.close()
                Log.d(TAG, "AcceptThread: Server socket closed.")
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the server socket", e)
            }
        }
    }

    /** Client thread for initiating outgoing connections. */
    private inner class ConnectThread(device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            if (checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) { // Calls method of outer class
                try {
                    // Use MY_UUID for createRfcommSocketToServiceRecord
                    device.createRfcommSocketToServiceRecord(MY_UUID)
                } catch (e: IOException) {
                    Log.e(TAG, "Socket's create() method failed", e)
                    null
                }
            } else {
                Log.e(TAG, "BLUETOOTH_CONNECT permission not granted for createRfcommSocketToServiceRecord")
                null
            }
        }

        override fun run() {
            bluetoothAdapter?.cancelDiscovery() // Cancel discovery to speed up connection

            mmSocket?.let { socket ->
                try {
                    Log.d(TAG, "ConnectThread: Attempting to connect to remote device.")
                    socket.connect() // This call blocks until it succeeds or throws an exception.
                    Log.d(TAG, "ConnectThread: Connection successful.")
                    manageConnectedSocket(socket, socket.remoteDevice) // Calls method of outer class
                } catch (e: IOException) {
                    Log.e(TAG, "ConnectThread: Could not connect to the remote device.", e)
                    connectionFailed() // Calls method of outer class
                    try {
                        socket.close() // Close the socket on failure
                    } catch (closeException: IOException) {
                        Log.e(TAG, "Could not close the client socket", closeException)
                    }
                }
            } ?: run {
                Log.e(TAG, "ConnectThread: Socket is null, cannot connect.")
                connectionFailed() // Calls method of outer class
            }
        }

        fun cancel() {
            try {
                mmSocket?.close()
                Log.d(TAG, "ConnectThread: Client socket closed.")
            } catch (e: IOException) {
                Log.e(TAG, "Could not close the client socket", e)
            }
        }
    }

    // --- Activity Lifecycle Methods ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_transfer_data) // Use activity_transfer_data.xml

        // Initialize UI elements
        tvTransferStatus = findViewById(R.id.tv_transfer_status)
        btnEnableBluetooth = findViewById(R.id.btn_enable_bluetooth)
        btnMakeDiscoverable = findViewById(R.id.btn_make_discoverable)
        btnScanDevices = findViewById(R.id.btn_scan_devices)
        lvPairedDevices = findViewById(R.id.lv_paired_devices)
        lvDiscoveredDevices = findViewById(R.id.lv_discovered_devices)
        btnSendData = findViewById(R.id.btn_send_data)
        btnReceiveData = findViewById(R.id.btn_receive_data)

        // Get Bluetooth adapter from the system
        val bluetoothManager: BluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Setup ListViews and their Adapters
        pairedDevicesArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, pairedDevicesList)
        lvPairedDevices.adapter = pairedDevicesArrayAdapter

        discoveredDevicesArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, discoveredDevicesList)
        lvDiscoveredDevices.adapter = discoveredDevicesArrayAdapter

        // Register the BroadcastReceiver for Bluetooth events
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        registerReceiver(bluetoothReceiver, filter)

        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        requestBluetoothPermissions() // Calls method defined later
    }

    override fun onPause() {
        super.onPause()
        bluetoothAdapter?.cancelDiscovery()
    }

    override fun onDestroy() {
        super.onDestroy()
        bluetoothAdapter?.cancelDiscovery()
        unregisterReceiver(bluetoothReceiver)
        bluetoothService?.cancel()
        acceptThread?.cancel()
        connectThread?.cancel()
    }

    // --- UI Setup and Click Listeners ---
    private fun setupClickListeners() {
        btnEnableBluetooth.setOnClickListener {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }

        btnMakeDiscoverable.setOnClickListener {
            val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
            }
            requestDiscoverableLauncher.launch(discoverableIntent)
        }

        btnScanDevices.setOnClickListener {
            startDiscovery() // Calls method defined later
        }

        lvPairedDevices.setOnItemClickListener { _, _, position, _ ->
            bluetoothAdapter?.cancelDiscovery()
            val info = pairedDevicesList[position]
            val address = info.substring(info.length - 17)
            val device = bluetoothAdapter?.getRemoteDevice(address)
            if (device != null && checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) { // Calls method defined later
                connectDevice(device) // Calls method defined later
            } else {
                Toast.makeText(this, "Could not connect. Check permissions or device availability.", Toast.LENGTH_SHORT).show()
                checkBluetoothState() // Calls method defined later
            }
        }

        lvDiscoveredDevices.setOnItemClickListener { _, _, position, _ ->
            bluetoothAdapter?.cancelDiscovery()
            val info = discoveredDevicesList[position]
            val address = info.substring(info.length - 17)
            val device = discoveredBluetoothDevices[address]

            if (device != null) {
                if (checkPermission(Manifest.permission.BLUETOOTH_CONNECT)) { // Calls method defined later
                    if (device.bondState == BluetoothDevice.BOND_NONE) {
                        Toast.makeText(this, "Attempting to pair with ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
                        device.createBond()
                    } else if (device.bondState == BluetoothDevice.BOND_BONDED) {
                        Toast.makeText(this, "Device already paired. Attempting to connect to ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
                        connectDevice(device) // Calls method defined later
                    }
                } else {
                    Toast.makeText(this, "BLUETOOTH_CONNECT permission required for pairing/connection.", Toast.LENGTH_SHORT).show()
                    requestBluetoothPermissions() // Calls method defined later
                }
            } else {
                Toast.makeText(this, "Could not get device info. Try scanning again.", Toast.LENGTH_SHORT).show()
            }
        }

        btnSendData.setOnClickListener {
            if (bluetoothService != null && bluetoothService!!.isConnected) {
                val fileHandler = FileHandler(this, STOCK_DATA_FILENAME)
                val stockEntries = fileHandler.loadStockEntries()
                if (stockEntries.isNotEmpty()) {
                    val jsonString = Gson().toJson(stockEntries)
                    bluetoothService?.write(jsonString.toByteArray())
                    Toast.makeText(this, "Records sent!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "No records to send.", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "Not connected to a device. Please connect first.", Toast.LENGTH_SHORT).show()
            }
        }

        btnReceiveData.setOnClickListener {
            if (bluetoothService != null && bluetoothService!!.isConnected) {
                Toast.makeText(this, "Ready to receive data.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Not connected. Start server or connect to receive.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /** Processes received JSON data and imports them into the app's local database. */
    private fun processReceivedData(jsonData: String) {
        val listType = object : TypeToken<List<StockEntry>>() {}.type
        val receivedEntries: List<StockEntry>? = try {
            Gson().fromJson(jsonData, listType)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse received JSON data", e)
            Toast.makeText(this, "Failed to import received data: Invalid format.", Toast.LENGTH_LONG).show()
            null
        }

        receivedEntries?.let {
            if (it.isNotEmpty()) {
                val fileHandler = FileHandler(this, STOCK_DATA_FILENAME)
                fileHandler.addMultipleStockEntries(it)
                Toast.makeText(this, "Successfully imported ${it.size} records via Bluetooth!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Received empty record list.", Toast.LENGTH_SHORT).show()
            }
        }
    }
}