package com.example.gostock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu

import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import androidx.core.content.edit


class HomeActivity : AppCompatActivity() {

    private lateinit var btnStartNewRecord: Button
    private lateinit var btnEditRecords: Button
    private lateinit var btnExportRecords: Button
    private lateinit var btnExportClose: Button
    private lateinit var btnImportRecords: Button
    private lateinit var btnManageUsers: Button

    private lateinit var btnBluetoothConnect: Button

    private lateinit var btnSettings: Button

    private lateinit var tvLoggedInUser: TextView

    private lateinit var fileHandler: FileHandler

    // Enum to distinguish between export types
    private enum class ExportType {
        EXPORT_ONLY, EXPORT_AND_CLEAR
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        btnStartNewRecord = findViewById(R.id.btn_start_new_record)
        btnEditRecords = findViewById(R.id.btn_edit_records)
        btnExportRecords = findViewById(R.id.btn_export_records)
        btnExportClose = findViewById(R.id.btn_export_close) // Initialize new button
        btnImportRecords = findViewById(R.id.btn_import_records)
        btnManageUsers = findViewById(R.id.btn_manage_users)
        btnSettings = findViewById(R.id.btn_settings)

        btnBluetoothConnect = findViewById(R.id.btn_bluetooth_connect)

        tvLoggedInUser = findViewById(R.id.tv_logged_in_user)

        fileHandler = FileHandler(this)

        setupUserDetails()
        setupClickListeners()
        setupRoleBasedVisibility()
    }

    // SAF Activity Result Launcher for importing a CSV file
    private val importDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri? = result.data?.data
            uri?.let { fileUri ->
                importRecordsFromCsv(fileUri)
            } ?: run {
                Toast.makeText(this, "File selection cancelled or failed.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Import cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun importRecordsFromCsv(uri: Uri) {

        val fileName = contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (cursor.moveToFirst() && nameIndex != -1) {
                cursor.getString(nameIndex)
            } else {
                null
            }
        }
        if (fileName == null || !fileName.lowercase(Locale.ROOT).endsWith(".csv")) {
            Toast.makeText(this, "Please select a valid .csv file.", Toast.LENGTH_LONG).show()
            return // Stop import if not a CSV
        }

        val importedEntries = mutableListOf<StockEntry>()
        var importedCount = 0

        try {
            contentResolver.openInputStream(uri)?.use { inputStream: InputStream ->
                val reader = BufferedReader(InputStreamReader(inputStream))
                var line: String?

                // Read and skip header line
                line = reader.readLine()
                if (line == null) {
                    Toast.makeText(this, "Selected CSV file is empty.", Toast.LENGTH_SHORT).show()
                    return
                }

                while (reader.readLine().also { line = it } != null) {
                    val columns = parseCsvLine(line!!) // Use helper to parse line
                    if (columns.size == 6) { // Expecting ID, Timestamp, Username, Location, SKU, Quantity
                        try {
                            val timestamp = columns[1]
                            val username = columns[2]
                            val locationBarcode = columns[3]
                            val skuBarcode = columns[4]
                            val quantity = columns[5].toInt()

                            // IMPORTANT: Generate a new UUID for the imported record
                            // This avoids ID conflicts if you import data from another device
                            val newEntry = StockEntry(
                                timestamp = timestamp,
                                username = username,
                                locationBarcode = locationBarcode,
                                skuBarcode = skuBarcode,
                                quantity = quantity
                            )
                            importedEntries.add(newEntry)
                            importedCount++
                        } catch (e: NumberFormatException) {
                            Toast.makeText(this, "Skipping row due to invalid quantity: ${columns[5]}", Toast.LENGTH_SHORT).show()
                            e.printStackTrace()
                        } catch (e: Exception) {
                            Toast.makeText(this, "Skipping row due to parsing error: ${e.message}", Toast.LENGTH_SHORT).show()
                            e.printStackTrace()
                        }
                    } else {
                        Toast.makeText(this, "Skipping row due to incorrect column count: ${columns.size}", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            if (importedEntries.isNotEmpty()) {
                fileHandler.addMultipleStockEntries(importedEntries) // Add all imported entries
                Toast.makeText(this, "Successfully imported $importedCount records!", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "No valid records found to import.", Toast.LENGTH_LONG).show()
            }

        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error reading CSV file: ${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "An unexpected error occurred during import: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Helper function to parse a CSV line, handling quotes and commas within fields
    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var inQuote = false
        val sb = StringBuilder()

        for (i in line.indices) {
            when (val char = line[i]) {
                '"' -> {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        // Escaped double quote ""
                        sb.append('"')
                        i.inc() // Skip next quote
                    } else {
                        // Start or end of a field quote
                        inQuote = !inQuote
                    }
                }
                ',' -> {
                    if (inQuote) {
                        sb.append(',')
                    } else {
                        result.add(sb.toString())
                        sb.clear()
                    }
                }
                else -> {
                    sb.append(char)
                }
            }
        }
        result.add(sb.toString()) // Add the last field

        // Unescape any quotes within fields
        return result.map { it.replace("\"\"", "\"") }
    }

    private fun setupUserDetails() {
        val loggedInUser = GoStockApp.loggedInUser
        if (loggedInUser != null) {
            tvLoggedInUser.text = loggedInUser.username
            tvLoggedInUser.setOnClickListener { view ->
                showUserMenu(view)
            }
        } else {
            tvLoggedInUser.text = getString(R.string.not_logged_in)
            Toast.makeText(this, "User not logged in. Redirecting to login.", Toast.LENGTH_LONG).show()
            performLogout()
        }
    }

    private fun setupRoleBasedVisibility() {
        val loggedInUser = GoStockApp.loggedInUser
        if (loggedInUser?.role == UserRole.ADMIN) {
            btnManageUsers.visibility = View.VISIBLE
        } else {
            btnManageUsers.visibility = View.GONE
        }
    }

    private fun showUserMenu(view: View) {
        val popup = PopupMenu(this, view, Gravity.END)
        popup.menuInflater.inflate(R.menu.user_menu, popup.menu)

        val themeToggleMenuItem = popup.menu.findItem(R.id.action_toggle_theme)
        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            themeToggleMenuItem.title = "Switch to Light Mode"
        } else {
            themeToggleMenuItem.title = "Switch to Dark Mode"
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_change_password -> {
                    val intent = Intent(this, ChangePasswordActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.action_toggle_theme -> {
                    toggleTheme()
                    true
                }
                R.id.action_logout -> {
                    performLogout()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun performLogout() {
        (application as GoStockApp).clearLoginSession()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
        Toast.makeText(this, "Logged out successfully!", Toast.LENGTH_SHORT).show()
    }

    private fun toggleTheme() {
        val currentNightMode = AppCompatDelegate.getDefaultNightMode()
        val newNightMode = if (currentNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
            AppCompatDelegate.MODE_NIGHT_NO
        } else {
            AppCompatDelegate.MODE_NIGHT_YES
        }

        AppCompatDelegate.setDefaultNightMode(newNightMode)
        val sharedPrefs = getSharedPreferences(GoStockApp.PREFS_FILE_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit { putInt(GoStockApp.KEY_THEME_MODE, newNightMode) }
        Toast.makeText(this, "Theme switched!", Toast.LENGTH_SHORT).show()
    }

    private fun setupClickListeners() {
        btnStartNewRecord.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        btnEditRecords.setOnClickListener {
            val intent = Intent(this, RecordListActivity::class.java)
            startActivity(intent)
        }

        btnExportRecords.setOnClickListener {
            initiateSafExport(ExportType.EXPORT_ONLY) // Export only
        }

        btnExportClose.setOnClickListener {
            initiateSafExport(ExportType.EXPORT_AND_CLEAR) // Export and clear
        }

        btnImportRecords.setOnClickListener {
            // Trigger SAF to open a CSV file
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*" // Change to accept ALL file types
            }
            importDocumentLauncher.launch(intent)
        }

        btnBluetoothConnect.setOnClickListener {
            val intent = Intent(this, BluetoothActivity::class.java)
            startActivity(intent)
        }

        btnManageUsers.setOnClickListener {
            val intent = Intent(this, UserManagementActivity::class.java)
            startActivity(intent)
        }

        btnSettings.setOnClickListener {
            val intent = Intent(this, SettingsActivity::class.java)
            startActivity(intent)
        }
    }

    // New: Generic function to initiate SAF export
    private fun initiateSafExport(exportType: ExportType) {
        val records = fileHandler.loadStockEntries()

        if (records.isEmpty()) {
            Toast.makeText(this, "No records to export!", Toast.LENGTH_SHORT).show()
            return
        }

        val csvFileName = "stock_records_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, csvFileName)
        }

        // Use the appropriate launcher based on export type
        when (exportType) {
            ExportType.EXPORT_ONLY -> createDocumentLauncher.launch(intent)
            ExportType.EXPORT_AND_CLEAR -> exportAndClearLauncher.launch(intent)
        }
    }


    // Existing: SAF Launcher for "Export All Records"
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri? = result.data?.data
            uri?.let { fileUri ->
                val recordsToExport = fileHandler.loadStockEntries()
                val success = writeCsvToUri(fileUri, recordsToExport) // Use helper function

                if (success) {
                    Toast.makeText(this, "Records exported successfully!", Toast.LENGTH_LONG).show()

                } else {
                    Toast.makeText(this, "Failed to export records.", Toast.LENGTH_LONG).show()
                }
            } ?: run {
                Toast.makeText(this, "File creation cancelled or failed.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Export cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    // New: SAF Launcher for "Export & Clear Database"
    private val exportAndClearLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri? = result.data?.data
            uri?.let { fileUri ->
                val recordsToExport = fileHandler.loadStockEntries()
                val success = writeCsvToUri(fileUri, recordsToExport) // Use helper function

                if (success) {
                    // Only clear if export was successful
                    val cleared = fileHandler.clearStockEntries()
                    if (cleared) {
                        Toast.makeText(this, "Records exported and database cleared!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "Records exported but failed to clear database.", Toast.LENGTH_LONG).show()
                    }

                } else {
                    Toast.makeText(this, "Failed to export records before clearing.", Toast.LENGTH_LONG).show()
                }
            } ?: run {
                Toast.makeText(this, "File creation cancelled or failed for Export & Clear.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Export & Clear cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    // New: Helper function to write CSV data to a given URI
    private fun writeCsvToUri(uri: Uri, records: List<StockEntry>): Boolean {
        val csvBuilder = StringBuilder()
        csvBuilder.append("ID,Timestamp,Username,LocationBarcode,SkuBarcode,Quantity\n")
        for (record in records) {
            csvBuilder.append("${escapeCsv(record.id)},")
            csvBuilder.append("${escapeCsv(record.timestamp)},")
            csvBuilder.append("${escapeCsv(record.username)},")
            csvBuilder.append("${escapeCsv(record.locationBarcode)},")
            csvBuilder.append("${escapeCsv(record.skuBarcode)},")
            csvBuilder.append("${record.quantity}\n")
        }

        return try {
            contentResolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
                outputStream.write(csvBuilder.toString().toByteArray())
                true
            } ?: false
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    // Helper function to escape commas and quotes in CSV fields (remains the same)
    private fun escapeCsv(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }

    // Helper function to open the CSV file (remains the same)
    private fun openCsvFile(fileUri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(fileUri, "text/csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open CSV files.", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
}