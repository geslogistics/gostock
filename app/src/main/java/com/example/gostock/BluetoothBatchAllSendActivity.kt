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
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.concurrent.thread
import kotlin.math.min

@SuppressLint("MissingPermission")
class BluetoothBatchAllSendActivity : AppCompatActivity() {

    // --- UI elements declarations ---
    private lateinit var btnToolbarBack: ImageButton
    private lateinit var tvTransferStatus: TextView
    private lateinit var btnEnableBluetooth: Button
    private lateinit var btnScanDevices: Button
    private lateinit var lvPairedDevices: ListView
    private lateinit var lvDiscoveredDevices: ListView
    private lateinit var btnSendData: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgressPercent: TextView

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
        private val MY_UUID: UUID = UUID.fromString("8cc7117b-eca7-4c31-820f-26ed27198bb1")
        private const val APP_NAME = "GoStockBatchTransfer"
        private const val TAG = "BluetoothBatchAllSendActivity"
        const val GO_DATA_FILENAME = "go_data.json"
    }

    private enum class BluetoothState {
        PERMISSIONS_DENIED, DISABLED, ENABLED, DISCOVERING, LISTENING, CONNECTING, CONNECTED
    }

    // --- ActivityResultLaunchers ---
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
        btnScanDevices.visibility = View.VISIBLE
        btnSendData.visibility = View.VISIBLE
    }
    private val requestDiscoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result -> if (result.resultCode > 0) startServer() }

    // --- Activity Lifecycle ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth_batch_all_send)
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
        btnScanDevices.setOnClickListener { startDiscovery() }
        lvPairedDevices.setOnItemClickListener { _, _, pos, _ ->
            bluetoothAdapter?.cancelDiscovery()
            connectToDevice(pairedDevicesList[pos])
        }
        lvDiscoveredDevices.setOnItemClickListener { _, _, pos, _ ->
            bluetoothAdapter?.cancelDiscovery()
            val device = discoveredDevicesList[pos]
            if (device.bondState == BluetoothDevice.BOND_BONDED) {
                connectToDevice(device)
            } else {
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
        if (loggedInUser == null) {
            showSnackbar("User not logged in. Redirecting...", Snackbar.LENGTH_LONG)
            performLogout()
        }
    }

    // --- Core Bluetooth Logic ---

    private fun startServer() {
        updateUiForListening()
        serverThread = thread(start = true) {
            try {
                val serverSocket: BluetoothServerSocket? = bluetoothAdapter?.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, MY_UUID)
                Log.d(TAG, "Server: Listening for connections...")
                val socket = serverSocket?.accept()
                serverSocket?.close()
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
                receiveData(socket)
            }
        }
    }

    // --- Data Transfer Logic ---

    private fun sendData(socket: BluetoothSocket) {
        lifecycleScope.launch(Dispatchers.IO) {
            // --- UPDATED: Use JsonFileHandler ---
            val stockListType = object : TypeToken<MutableList<BatchEntry>>() {}
            val goDataFileHandler = JsonFileHandler(this@BluetoothBatchAllSendActivity, GO_DATA_FILENAME, stockListType)
            val recordsToSend = goDataFileHandler.loadRecords()

            if (recordsToSend.isEmpty()) {
                withContext(Dispatchers.Main) { Toast.makeText(this@BluetoothBatchAllSendActivity, "No batch data to send.", Toast.LENGTH_SHORT).show() }
                return@launch
            }

            withContext(Dispatchers.Main) { updateUiForTransfer("Sending all batches...") }

            var transferSucceeded = false
            try {
                DataOutputStream(socket.outputStream).use { dataOut ->
                    val file = File(filesDir, GO_DATA_FILENAME)
                    FileInputStream(file).use { fileIn ->
                        val fileSize = file.length()
                        dataOut.writeLong(fileSize)
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesSent: Long = 0
                        while (fileIn.read(buffer).also { bytesRead = it } != -1) {
                            dataOut.write(buffer, 0, bytesRead)
                            totalBytesSent += bytesRead
                            val progress = (totalBytesSent * 100 / fileSize).toInt()
                            withContext(Dispatchers.Main) { updateProgress(progress) }
                        }
                        dataOut.flush()
                        transferSucceeded = true
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) { updateUiForReadyState("❗  Send failed") }
            } finally {
                socket.close()
            }

            if (transferSucceeded) {
                val actionUser = GoStockApp.loggedInUser?.username ?: "Unknown"
                val actionTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val enrichedRecords = recordsToSend

                // --- UPDATED: Use JsonFileHandler for deleted file ---
                val deletedFileHandler = JsonFileHandler(this@BluetoothBatchAllSendActivity, "go_deleted.json", stockListType)
                deletedFileHandler.addMultipleRecords(enrichedRecords)

                goDataFileHandler.clearData()
                Log.d(TAG, "Sender: Archived and cleared go_data.json")

                navigateToHome()
            }
        }
    }

    private fun receiveData(socket: BluetoothSocket) {
        lifecycleScope.launch(Dispatchers.IO) {
            val tempFile = File(cacheDir, "received_all_batches.json")
            withContext(Dispatchers.Main) { updateUiForTransfer("\uD83D\uDD35 Receiving all batches...") }
            try {
                DataInputStream(socket.inputStream).use { dataIn ->
                    FileOutputStream(tempFile).use { fileOut ->
                        val fileSize = dataIn.readLong()
                        if (fileSize == 0L) return@launch
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
                    }
                }
                processReceivedFile(tempFile)
            } catch (e: IOException) {
                withContext(Dispatchers.Main) { updateUiForReadyState("❗  Receive failed") }
            } finally {
                socket.close()
                tempFile.delete()
            }
        }
    }

    private fun processReceivedFile(receivedFile: File) {
        try {
            val listType = object : TypeToken<MutableList<BatchEntry>>() {}.type
            val receivedEntries: MutableList<BatchEntry> = FileReader(receivedFile).use { reader ->
                Gson().fromJson(reader, listType) ?: mutableListOf()
            }
            if (receivedEntries.isNotEmpty()) {
                // --- UPDATED: Use JsonFileHandler to save received data ---
                val stockListType = object : TypeToken<MutableList<BatchEntry>>() {}
                val goDataFileHandler = JsonFileHandler(this, "go_data.json", stockListType)
                
                goDataFileHandler.addMultipleRecords(receivedEntries)
                navigateToHome()
            } else {
                runOnUiThread { Toast.makeText(this, "Received file contained no records.", Toast.LENGTH_SHORT).show() }
            }
        } catch (e: Exception) {
            runOnUiThread { Toast.makeText(this, "Failed to process: Invalid data format.", Toast.LENGTH_LONG).show() }
        }
    }

    // --- All other UI and helper functions remain here ---
    private fun showSnackbar(message: String, duration: Int = Snackbar.LENGTH_LONG) {
        val snackbar = Snackbar.make(findViewById(android.R.id.content), message, duration)
        val snackbarTextView = snackbar.view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
        snackbarTextView.maxLines = 5
        snackbarTextView.ellipsize = null
        snackbar.show()
    }
    private fun performLogout() {
        (application as GoStockApp).clearLoginSession()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }
    private fun navigateToHome() {
        runOnUiThread {
            Toast.makeText(this, "✅ Transfer successful.", Toast.LENGTH_SHORT).show()
            val intent = Intent(this, HomeActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            startActivity(intent)
            finish()
        }
    }
    private fun updateProgress(progress: Int) {
        progressBar.progress = progress
        tvProgressPercent.text = "$progress%"
    }
    private fun updateUiForTransfer(status: String) {
        tvTransferStatus.text = status
        progressBar.visibility = View.VISIBLE
        tvProgressPercent.visibility = View.VISIBLE
        listOf(btnSendData, btnScanDevices, lvPairedDevices, lvDiscoveredDevices, btnToolbarBack).forEach { it.isEnabled = false }
        updateProgress(0)
    }
    private fun updateUiForReadyState(status: String = "⚪  Ready") {
        tvTransferStatus.text = status
        progressBar.visibility = View.GONE
        tvProgressPercent.visibility = View.GONE
        btnSendData.isEnabled = false
        btnScanDevices.isEnabled = true
        listOf(lvPairedDevices, lvDiscoveredDevices, btnToolbarBack).forEach { it.isEnabled = true }
        setupRoleBasedUI()
    }
    private fun updateUiForConnected(deviceName: String) {
        tvTransferStatus.text = "\uD83D\uDFE2  Connected to $deviceName"
        progressBar.visibility = View.GONE
        tvProgressPercent.visibility = View.GONE
        btnSendData.isEnabled = true
        btnScanDevices.isEnabled = false
    }
    private fun updateUiForListening() {
        runOnUiThread {
            tvTransferStatus.text = "\uD83D\uDD35  Listening for connections..."
            btnScanDevices.isEnabled = false
        }
    }
    private fun updateUiForConnecting(deviceName: String) {
        tvTransferStatus.text = "\uD83D\uDFE1  Connecting to $deviceName..."
        btnScanDevices.isEnabled = false
    }
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
            listOf(btnScanDevices, btnSendData).forEach { it.visibility = View.GONE }
            return
        }
        if (bluetoothAdapter?.isEnabled == true) {
            updateUiForReadyState()
            listPairedDevices()
        } else {
            btnEnableBluetooth.visibility = View.VISIBLE
            listOf(btnScanDevices, btnSendData).forEach { it.visibility = View.GONE }
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
                tvTransferStatus.text = "⚪  Ready"
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
}
