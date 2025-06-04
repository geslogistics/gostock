package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
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
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class BluetoothActivity : AppCompatActivity() {

    private lateinit var tvBluetoothStatus: TextView
    private lateinit var btnEnableBluetooth: Button
    private lateinit var btnScanDevices: Button
    private lateinit var lvFoundDevices: ListView

    private var bluetoothAdapter: BluetoothAdapter? = null
    private lateinit var devicesArrayAdapter: ArrayAdapter<String>
    private val foundDevicesList = mutableListOf<String>()
    private val discoveredDevices = mutableMapOf<String, BluetoothDevice>() // Stores actual BluetoothDevice objects

    private val TAG = "BluetoothActivity"

    // Request codes for permissions and enabling Bluetooth
    companion object {
        private const val REQUEST_CODE_BLUETOOTH_PERMISSIONS = 1
        private const val REQUEST_CODE_ENABLE_BLUETOOTH = 2
    }

    // Launcher for requesting Bluetooth permissions (BLUETOOTH_SCAN, BLUETOOTH_CONNECT, etc.)
    private val requestBluetoothPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Toast.makeText(this, "Bluetooth permissions granted.", Toast.LENGTH_SHORT).show()
            checkBluetoothState() // Re-check state after permissions are granted
        } else {
            Toast.makeText(this, "Bluetooth permissions denied. Cannot use Bluetooth features.", Toast.LENGTH_LONG).show()
            tvBluetoothStatus.text = "Bluetooth Status: Permissions Denied"
            btnScanDevices.isEnabled = false
        }
    }

    // Launcher for enabling Bluetooth (ACTION_REQUEST_ENABLE)
    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Bluetooth enabled.", Toast.LENGTH_SHORT).show()
            // Add a small delay here to give BT adapter time to initialize fully
            Handler(Looper.getMainLooper()).postDelayed({
                checkBluetoothState()
            }, 500) // 500ms delay
        } else {
            Toast.makeText(this, "Bluetooth not enabled. Cannot proceed.", Toast.LENGTH_SHORT).show()
            tvBluetoothStatus.text = "Bluetooth Status: Disabled"
            btnScanDevices.isEnabled = false
        }
    }

    // Launcher for requesting enabling Location Services (ACTION_LOCATION_SOURCE_SETTINGS)
    private val requestLocationServicesLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        // After returning from location settings, re-check Bluetooth state
        checkBluetoothState()
    }

    // Launcher for requesting device discoverability (ACTION_REQUEST_DISCOVERABLE)
    private val requestDiscoverableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) { // User allowed discoverability
            Toast.makeText(this, "Device discoverable for ${result.resultCode} seconds.", Toast.LENGTH_SHORT).show()
            // Now that device is discoverable, try starting discovery
            startDiscoveryInternal()
        } else {
            Toast.makeText(this, "Device not made discoverable. Scan might be limited.", Toast.LENGTH_LONG).show()
            // Still try to scan, but inform user
            startDiscoveryInternal() // Try to scan anyway, but it might fail
        }
    }

    // BroadcastReceiver for Bluetooth device discovery events
    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // A new Bluetooth device was found during discovery
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        // Ensure device has a name and is not already in our list
                        // BLUETOOTH_CONNECT permission is needed to get the device name on API 31+
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                            if (it.name != null && !foundDevicesList.contains("${it.name}\n${it.address}")) {
                                foundDevicesList.add("${it.name}\n${it.address}")
                                discoveredDevices[it.address] = it // Store the actual BluetoothDevice object
                                devicesArrayAdapter.notifyDataSetChanged() // Update the ListView
                                Toast.makeText(context, "Found device: ${it.name}", Toast.LENGTH_SHORT).show()
                            }
                        } else {
                            // If BLUETOOTH_CONNECT is not granted, we can only show address
                            if (!foundDevicesList.contains("Unknown Device\n${it.address}")) {
                                foundDevicesList.add("Unknown Device\n${it.address}")
                                discoveredDevices[it.address] = it
                                devicesArrayAdapter.notifyDataSetChanged()
                                Toast.makeText(context, "Found device: ${it.address}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    // Bluetooth discovery has started
                    Toast.makeText(context, "Scanning started...", Toast.LENGTH_SHORT).show()
                    foundDevicesList.clear() // Clear previous list
                    discoveredDevices.clear() // Clear previous device objects
                    devicesArrayAdapter.notifyDataSetChanged()
                    tvBluetoothStatus.text = "Bluetooth Status: Scanning..."
                    btnScanDevices.isEnabled = false // Disable scan button during scan
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    // Bluetooth discovery has finished
                    Toast.makeText(context, "Scanning finished.", Toast.LENGTH_SHORT).show()
                    tvBluetoothStatus.text = "Bluetooth Status: Enabled (Scan Finished)"
                    btnScanDevices.isEnabled = true // Re-enable scan button
                    if (foundDevicesList.isEmpty()) {
                        Toast.makeText(context, "No devices found.", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth)

        tvBluetoothStatus = findViewById(R.id.tv_bluetooth_status)
        btnEnableBluetooth = findViewById(R.id.btn_enable_bluetooth)
        btnScanDevices = findViewById(R.id.btn_scan_devices)
        lvFoundDevices = findViewById(R.id.lv_found_devices)

        // Get Bluetooth adapter
        val bluetoothManager: BluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        // Setup ListView for devices
        devicesArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, foundDevicesList)
        lvFoundDevices.adapter = devicesArrayAdapter

        // Register the BroadcastReceiver for Bluetooth discovery events
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(bluetoothReceiver, filter)

        setupClickListeners()
    }

    override fun onResume() {
        super.onResume()
        requestBluetoothPermissions() // Request permissions every time activity resumes
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(bluetoothReceiver) // IMPORTANT: Unregister receiver to prevent memory leaks
        // Stop any ongoing discovery when activity is destroyed
        bluetoothAdapter?.cancelDiscovery()
    }

    private fun setupClickListeners() {
        btnEnableBluetooth.setOnClickListener {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            enableBluetoothLauncher.launch(enableBtIntent)
        }

        btnScanDevices.setOnClickListener {
            startDiscovery() // Call the public startDiscovery() which requests discoverability first
        }
    }

    // Public function to initiate Bluetooth device discovery (requests discoverability first)
    private fun startDiscovery() {
        Log.d(TAG, "startDiscovery() called (public). Requesting discoverability.")
        // Request device discoverability. This can sometimes help startDiscovery() succeed.
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            // EXTRA_DISCOVERABLE_DURATION can be up to 300 seconds (5 minutes)
            // 120 seconds is a common duration.
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120)
        }
        requestDiscoverableLauncher.launch(discoverableIntent)
    }

    // Internal function to actually start the Bluetooth device discovery after checks/prompts
    private fun startDiscoveryInternal() {
        Log.d(TAG, "startDiscoveryInternal() called.")

        // First, ensure Bluetooth is actually enabled
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Toast.makeText(this, "Bluetooth is not enabled.", Toast.LENGTH_SHORT).show()
            checkBluetoothState() // Update UI status
            Log.w(TAG, "Bluetooth not enabled, cannot start discovery internally.")
            return
        }

        // Check if Location Services are enabled (required for Bluetooth scanning on Android 6.0+)
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val isLocationEnabled = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            locationManager.isLocationEnabled
        } else {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        }

        if (!isLocationEnabled) {
            Toast.makeText(this, "Location Services must be enabled for Bluetooth scanning.", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
            requestLocationServicesLauncher.launch(intent)
            Log.w(TAG, "Location Services disabled, prompting user.")
            return
        }

        // Check for BLUETOOTH_SCAN permission (required for API 31+)
        // For API < 31, ACCESS_FINE_LOCATION or COARSE_LOCATION is needed for scan
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "BLUETOOTH_SCAN permission not granted.", Toast.LENGTH_LONG).show()
                requestBluetoothPermissions() // Re-request permissions
                Log.w(TAG, "BLUETOOTH_SCAN permission missing, re-requesting.")
                return
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Location permission not granted for Bluetooth scan.", Toast.LENGTH_LONG).show()
                requestBluetoothPermissions() // Re-request permissions
                Log.w(TAG, "Location permission missing for older API, re-requesting.")
                return
            }
        }

        // If already discovering, cancel it first
        if (bluetoothAdapter?.isDiscovering == true) {
            Log.d(TAG, "Cancelling ongoing discovery before new internal scan.")
            // BLUETOOTH_ADMIN permission is needed for cancelDiscovery() on older APIs
            // BLUETOOTH_CONNECT is needed on API 31+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter?.cancelDiscovery()
                } else {
                    Log.w(TAG, "BLUETOOTH_CONNECT permission missing, cannot cancel discovery.")
                }
            } else {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED) {
                    bluetoothAdapter?.cancelDiscovery()
                } else {
                    Log.w(TAG, "BLUETOOTH_ADMIN permission missing, cannot cancel discovery.")
                }
            }
        }

        // Clear previous list before starting new discovery
        foundDevicesList.clear()
        discoveredDevices.clear()
        devicesArrayAdapter.notifyDataSetChanged()

        // Start new discovery
        val started = bluetoothAdapter?.startDiscovery()
        if (started == true) {
            Log.d(TAG, "Discovery successfully started internally.")
            Toast.makeText(this, "Starting device scan...", Toast.LENGTH_SHORT).show()
            tvBluetoothStatus.text = "Bluetooth Status: Scanning..."
            btnScanDevices.isEnabled = false
        } else {
            Log.e(TAG, "Failed to call startDiscovery() internally. startDiscovery() returned false.")
            Toast.makeText(this, "Failed to start device scan. Try restarting Bluetooth or device.", Toast.LENGTH_LONG).show()
            btnScanDevices.isEnabled = true
        }
    }

    private fun requestBluetoothPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
                Log.d(TAG, "Permissions: Adding BLUETOOTH_SCAN")
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
                Log.d(TAG, "Permissions: Adding BLUETOOTH_CONNECT")
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADVERTISE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADVERTISE)
                Log.d(TAG, "Permissions: Adding BLUETOOTH_ADVERTISE")
            }
        } else { // API < 31
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
                Log.d(TAG, "Permissions: Adding BLUETOOTH_ADMIN")
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
                Log.d(TAG, "Permissions: Adding ACCESS_FINE_LOCATION")
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                Log.d(TAG, "Permissions: Adding ACCESS_COARSE_LOCATION")
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            Log.d(TAG, "Permissions: Launching request for: ${permissionsToRequest.joinToString()}")
            requestBluetoothPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            Log.d(TAG, "Permissions: All required Bluetooth permissions already granted.")
            checkBluetoothState()
        }
    }

    private fun checkBluetoothState() {
        Log.d(TAG, "checkBluetoothState() called.")
        Log.d(TAG, "checkBluetoothState: BluetoothAdapter is null: ${bluetoothAdapter == null}")
        if (bluetoothAdapter != null) {
            Log.d(TAG, "checkBluetoothState: BluetoothAdapter is enabled: ${bluetoothAdapter!!.isEnabled}")
        }

        if (bluetoothAdapter == null) {
            tvBluetoothStatus.text = "Bluetooth Status: Not Supported"
            Toast.makeText(this, "Bluetooth is not supported on this device.", Toast.LENGTH_LONG).show()
            btnEnableBluetooth.visibility = View.GONE
            btnScanDevices.isEnabled = false
        } else {
            if (!bluetoothAdapter!!.isEnabled) {
                tvBluetoothStatus.text = "Bluetooth Status: Disabled"
                btnEnableBluetooth.visibility = View.VISIBLE
                btnScanDevices.isEnabled = false
            } else {
                tvBluetoothStatus.text = "Bluetooth Status: Enabled"
                btnEnableBluetooth.visibility = View.GONE
                btnScanDevices.isEnabled = true // Enable scan button if BT is enabled
            }
        }
    }
}