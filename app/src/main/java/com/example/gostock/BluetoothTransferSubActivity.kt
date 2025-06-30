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
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.util.*
import kotlin.math.min
import kotlin.concurrent.thread

// Your existing imports
import com.example.gostock.StockEntry
import com.example.gostock.FileHandler

@SuppressLint("MissingPermission")
class BluetoothTransferSubActivity : AppCompatActivity() {

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
    private lateinit var tvProgressPercent: TextView // For percentage text

    // --- Bluetooth core components ---
    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var pairedDevicesArrayAdapter: ArrayAdapter<String>
    private val pairedDevicesList = mutableListOf<BluetoothDevice>()
    private lateinit var discoveredDevicesArrayAdapter: ArrayAdapter<String>
    private val discoveredDevicesList = mutableListOf<BluetoothDevice>()
    private var connectedSocket: BluetoothSocket? = null
    private var serverThread: Thread? = null

    // --- Constants ---
    private companion object {
        private val MY_UUID: UUID = UUID.fromString("8cc7117b-eca7-4c31-820f-26ed27198bb0")
        private const val APP_NAME = "GoStockAppTransfer"
        private const val TAG = "BluetoothTransfer"
        const val STOCK_DATA_FILENAME = "stock_data.json"
    }

    // --- ActivityResultLaunchers (no changes needed) ---
    private val requestBluetoothPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) { checkBluetoothState() } else { showPermissionsDeniedDialog() }
    }
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> if (result.resultCode == Activity.RESULT_OK)
        checkBluetoothState()
        btnEnableBluetooth.visibility = View.GONE
        btnMakeDiscoverable.visibility = View.VISIBLE
        btnMakeDiscoverableDivider.visibility = View.VISIBLE
        btnScanDevices.visibility = View.VISIBLE
        btnSendData.visibility = View.VISIBLE
    }
    private val requestDiscoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> if (result.resultCode > 0) startServer() }

    // --- Activity Lifecycle ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_transfer_sub)
        initUI()
        initBluetooth()
        setupClickListeners()
        requestAllPermissions()
        setupRoleBasedUI()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver)
        serverThread?.interrupt()
        connectedSocket?.close()
    }

    // --- Initialization & Setup ---
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
                putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300) // Discoverable for 5 minutes
            }
            requestDiscoverableLauncher.launch(discoverableIntent)
        }
        btnScanDevices.setOnClickListener { startDiscovery() }
        btnScanDevices.setOnClickListener { startDiscovery() }

        // This listener for already paired devices is correct.
        lvPairedDevices.setOnItemClickListener { _, _, pos, _ ->
            bluetoothAdapter?.cancelDiscovery()
            connectToDevice(pairedDevicesList[pos])
        }

        // --- THIS IS THE FIX ---
        // This listener is for newly discovered, unpaired devices.
        lvDiscoveredDevices.setOnItemClickListener { _, _, pos, _ ->
            bluetoothAdapter?.cancelDiscovery()
            val device = discoveredDevicesList[pos]

            // Check if the device is already paired. This is a safety check;
            // it shouldn't normally be in this list if it's already paired.
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                connectToDevice(device)
            } else {
                // If the device is not paired, call createBond() to start the pairing process.
                // This will trigger the system pairing dialog on both devices.
                Log.d(TAG, "Device not paired. Initiating bond with ${device.name}")
                device.createBond()
            }
        }

        btnSendData.setOnClickListener {
            connectedSocket?.let { socket -> sendData(socket) }
                ?: Toast.makeText(this, "Not connected to a device", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setupRoleBasedUI() {
        val loggedInUser = GoStockApp.loggedInUser
        if (loggedInUser != null) {
            if (loggedInUser.role == UserRole.STOCKTAKER) {
                btnMakeDiscoverable.visibility = View.GONE
                btnMakeDiscoverableDivider.visibility = View.GONE
            }
        } else {
            showSnackbar("User not logged in. Redirecting...", Snackbar.LENGTH_LONG)
            performLogout()
        }
    }

    // --- Core Bluetooth Logic (Rewritten with Coroutines and Streaming) ---

    private fun startServer() {
        updateUiForListening()
        serverThread = thread(start = true) {
            try {
                val serverSocket: BluetoothServerSocket? = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, MY_UUID)
                Log.d(TAG, "Server: Listening for connections...")
                val socket = serverSocket?.accept() // This blocks the thread
                serverSocket?.close() // We have a connection, stop listening
                socket?.let {
                    Log.d(TAG, "Server: Connection accepted.")
                    manageConnectedSocket(it, isServer = true)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Server thread error", e)
                runOnUiThread { updateUiForReadyState("❗  Failed to listen") }
            }
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        updateUiForConnecting(device.name)
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val socket = device.createInsecureRfcommSocketToServiceRecord(MY_UUID)
                socket.connect()
                Log.d(TAG, "Client: Connection successful.")
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
                // If this device is the server, start listening for data right away.
                receiveData(socket)
            }
            // If this device is the client (sender), do nothing. Just wait in the
            // "Connected" state for the user to press the Send button.
        }
    }

    // --- NEW STREAMING DATA TRANSFER LOGIC ---

    private fun sendData(socket: BluetoothSocket) {
        lifecycleScope.launch(Dispatchers.IO) {
            val file = File(filesDir, STOCK_DATA_FILENAME)
            if (!file.exists() || file.length() == 0L) {
                withContext(Dispatchers.Main) { Toast.makeText(this@BluetoothTransferSubActivity, "No data to send.", Toast.LENGTH_SHORT).show() }
                return@launch
            }

            withContext(Dispatchers.Main) { updateUiForTransfer("Sending...") }

            try {
                // Use DataOutputStream to easily send the file size first
                DataOutputStream(socket.outputStream).use { dataOut ->
                    FileInputStream(file).use { fileIn ->
                        // 1. Handshake: Send file size (as a Long)
                        val fileSize = file.length()
                        dataOut.writeLong(fileSize)
                        Log.d(TAG, "Sender: Sent file size: $fileSize")

                        // 2. Stream file content in chunks
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesSent: Long = 0
                        while (fileIn.read(buffer).also { bytesRead = it } != -1) {
                            dataOut.write(buffer, 0, bytesRead)
                            totalBytesSent += bytesRead
                            val progress = (totalBytesSent * 100 / fileSize).toInt()
                            // Update UI on the main thread
                            withContext(Dispatchers.Main) { updateProgress(progress) }
                        }
                        dataOut.flush()
                        Log.d(TAG, "Sender: File sending complete.")
                    }
                }
                // If we reach here, the transfer was successful
                navigateToHome()
            } catch (e: IOException) {
                Log.e(TAG, "Error during sending", e)
                withContext(Dispatchers.Main) { updateUiForReadyState("❗  Send failed") }
            } finally {
                socket.close()
            }
        }
    }

    private fun receiveData(socket: BluetoothSocket) {
        lifecycleScope.launch(Dispatchers.IO) {
            val tempFile = File(cacheDir, "received_transfer.json")
            withContext(Dispatchers.Main) { updateUiForTransfer("\uD83D\uDD35 Receiving...") }

            try {
                DataInputStream(socket.inputStream).use { dataIn ->
                    FileOutputStream(tempFile).use { fileOut ->
                        // 1. Handshake: Read incoming file size
                        val fileSize = dataIn.readLong()
                        Log.d(TAG, "Receiver: Expecting file size: $fileSize")
                        if (fileSize == 0L) {
                            withContext(Dispatchers.Main) { Toast.makeText(this@BluetoothTransferSubActivity, "Received empty file.", Toast.LENGTH_SHORT).show() }
                            return@launch
                        }

                        // 2. Stream file content in chunks to a temporary file
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesReceived: Long = 0
                        while (totalBytesReceived < fileSize) {
                            bytesRead = dataIn.read(buffer, 0, min(buffer.size.toLong(), fileSize - totalBytesReceived).toInt())
                            if (bytesRead == -1) break
                            fileOut.write(buffer, 0, bytesRead)
                            totalBytesReceived += bytesRead
                            val progress = (totalBytesReceived * 100 / fileSize).toInt()
                            withContext(Dispatchers.Main) { updateProgress(progress) }
                        }
                        Log.d(TAG, "Receiver: File receiving complete. Total bytes: $totalBytesReceived")
                    }
                }

                // 3. If reception was successful, process the file from disk
                processReceivedFile(tempFile)

            } catch (e: IOException) {
                Log.e(TAG, "Error during receiving", e)
                withContext(Dispatchers.Main) { updateUiForReadyState("❗  Receive failed") }
            } finally {
                socket.close()
                tempFile.delete()
            }
        }
    }

    private fun processReceivedFile(receivedFile: File) {
        try {
            FileReader(receivedFile).use { reader ->
                val listType = object : TypeToken<List<StockEntry>>() {}.type
                val receivedEntries: List<StockEntry> = Gson().fromJson(reader, listType)

                if (receivedEntries.isNotEmpty()) {
                    val fileHandler = FileHandler(this, STOCK_DATA_FILENAME)
                    fileHandler.addMultipleStockEntries(receivedEntries)
                    navigateToHome()
                } else {
                    runOnUiThread { Toast.makeText(this, "Received file contained no records.", Toast.LENGTH_SHORT).show() }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse received file", e)
            runOnUiThread { Toast.makeText(this, "Failed to process: Invalid file format.", Toast.LENGTH_LONG).show() }
        }
    }

    // --- UI State Management ---
    private fun updateProgress(progress: Int) {
        progressBar.progress = progress
        tvProgressPercent.text = "$progress%"
    }

    private fun updateUiForTransfer(status: String) {
        tvTransferStatus.text = "$status"
        progressBar.visibility = View.VISIBLE
        tvProgressPercent.visibility = View.VISIBLE
        listOf(btnSendData, btnScanDevices, btnMakeDiscoverable, lvPairedDevices, lvDiscoveredDevices, btnToolbarBack).forEach { it.isEnabled = false }
        updateProgress(0)
    }

    private fun updateUiForReadyState(status: String = "⚪  Ready") {
        tvTransferStatus.text = "$status"
        progressBar.visibility = View.GONE
        tvProgressPercent.visibility = View.GONE
        btnSendData.isEnabled = false
        btnScanDevices.isEnabled = true
        btnMakeDiscoverable.isEnabled = true
        listOf(lvPairedDevices, lvDiscoveredDevices, btnToolbarBack).forEach { it.isEnabled = true }
        setupRoleBasedUI() // Re-apply role UI
    }

    private fun updateUiForConnected(deviceName: String) {
        tvTransferStatus.text = "\uD83D\uDFE2  Connected to $deviceName"
        progressBar.visibility = View.GONE
        tvProgressPercent.visibility = View.GONE
        btnSendData.isEnabled = true
        btnScanDevices.isEnabled = false
        btnMakeDiscoverable.isEnabled = false
    }

    private fun updateUiForListening() {
        runOnUiThread {
            tvTransferStatus.text = "\uD83D\uDD35  Listening for connections..."
            btnScanDevices.isEnabled = false
            btnMakeDiscoverable.isEnabled = false
        }
    }

    private fun updateUiForConnecting(deviceName: String) {
        tvTransferStatus.text = "\uD83D\uDFE1  Connecting to $deviceName..."
        btnScanDevices.isEnabled = false
        btnMakeDiscoverable.isEnabled = false
    }

    // --- Permissions and Other Helpers (mostly unchanged) ---
    private fun checkPermission(p: String): Boolean = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED

    private fun requestAllPermissions() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION)
        }
        val missing = permissions.filterNot { checkPermission(it) }
        if (missing.isNotEmpty()) {
            requestBluetoothPermissionsLauncher.launch(missing.toTypedArray())
        } else {
            checkBluetoothState()
        }
    }

    private fun checkBluetoothState() {
        if (bluetoothAdapter == null) {
            updateUiForReadyState("❗  Bluetooth not supported")
            btnMakeDiscoverable.visibility = View.GONE
            btnMakeDiscoverableDivider.visibility = View.GONE
            btnScanDevices.visibility = View.GONE
            btnSendData.visibility = View.GONE
            return
        }
        if (bluetoothAdapter?.isEnabled == true) {
            updateUiForReadyState()
            listPairedDevices()
        } else {
            btnEnableBluetooth.visibility = View.VISIBLE
            btnMakeDiscoverable.visibility = View.GONE
            btnMakeDiscoverableDivider.visibility = View.GONE
            btnScanDevices.visibility = View.GONE
            btnSendData.visibility = View.GONE
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

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action == BluetoothDevice.ACTION_FOUND) {
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
            } else if (action == BluetoothAdapter.ACTION_DISCOVERY_STARTED) {
                tvTransferStatus.text = "\uD83D\uDD35  Scanning..."
            } else if (action == BluetoothAdapter.ACTION_DISCOVERY_FINISHED) {
                tvTransferStatus.text = "\uD83D\uDD35  Ready"
            }
        }
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

    private fun performLogout() {
        (application as GoStockApp).clearLoginSession()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_LONG) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, duration)

        // Make Snackbar text multiline (works up to a certain point before system truncates)
        val snackbarTextView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        snackbarTextView.maxLines = 5 // Allow up to 5 lines (adjust as needed)
        snackbarTextView.ellipsize = null // Remove ellipsis if text exceeds maxLines

        snackbar.show()
    }

    private fun navigateToHome() {
        // Use runOnUiThread to ensure this can be called safely from any thread
        runOnUiThread {
            finish() // Close this BluetoothTransferSubActivity
            Toast.makeText(this, "✅ Transfer successful.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, HomeActivity::class.java)
            // These flags clear the other screens from history so the user can't press "back"
            // to return to the transfer screen.
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)

        }
    }
}