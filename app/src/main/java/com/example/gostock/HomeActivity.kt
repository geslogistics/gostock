package com.example.gostock

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import com.google.gson.reflect.TypeToken
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class HomeActivity : AppCompatActivity() {

    private lateinit var tvWelcomeSmallMessage: TextView
    private lateinit var tvWelcomeMessage: TextView
    private lateinit var llBatchSummary: LinearLayout
    private lateinit var llDashboard: LinearLayout
    private lateinit var llHdash: LinearLayout

    private lateinit var pbBatchSize: ProgressBar
    private lateinit var tvBatchSizeProgress: TextView
    private lateinit var tvMaxBatchSize: TextView

    private lateinit var pbBatchTime: ProgressBar
    private lateinit var tvBatchTimeProgress: TextView
    private lateinit var tvMaxBatchTime: TextView

    private lateinit var tvDashcardLocation: TextView
    private lateinit var tvDashcardSku: TextView
    private lateinit var tvDashcardQuantity: TextView

    private lateinit var btnStartNewRecord: LinearLayout
    private lateinit var btnMenuContinueBatch: LinearLayout
    private lateinit var btnEditRecords: LinearLayout
    private lateinit var btnExportRecords: LinearLayout
    private lateinit var btnExportClearRecords: LinearLayout
    private lateinit var btnImportRecords: LinearLayout
    private lateinit var btnManageUsers: LinearLayout
    private lateinit var btnTransferData: LinearLayout
    private lateinit var btnBatchList: LinearLayout
    private lateinit var btnSettings: LinearLayout
    private lateinit var tvLoggedInUser: TextView

    // --- UPDATED: Use the new generic JsonFileHandler ---
    private lateinit var stockEntryFileHandler: JsonFileHandler<StockEntry>
    private lateinit var stockEntryArchivedFileHandler: JsonFileHandler<StockEntryArchived>

    private enum class ExportType {
        EXPORT_ONLY, EXPORT_AND_CLEAR
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        initViews()

        val stockListType = object : TypeToken<MutableList<StockEntry>>() {}
        stockEntryFileHandler = JsonFileHandler(this, "stock_data.json", stockListType)

        val stockArchivedListType = object : TypeToken<MutableList<StockEntryArchived>>() {}
        stockEntryArchivedFileHandler = JsonFileHandler(this, "stock_deleted.json", stockArchivedListType)

        val loggedInUser = GoStockApp.loggedInUser
        if (loggedInUser == null) {
            Toast.makeText(this, "User not logged in. Redirecting to login.", Toast.LENGTH_LONG).show()
            performLogout()
            return // Stop further execution
        }

        setupUserDetails()
        setupClickListeners()
        setupRoleBasedVisibility()
    }

    override fun onResume() {
        super.onResume()
        setupUserDetails()
        setupRoleBasedVisibility()
        updateDashboard()
    }

    private fun initViews() {
        tvWelcomeSmallMessage = findViewById(R.id.tv_welcome_small_message)
        tvWelcomeMessage = findViewById(R.id.tv_welcome_message)
        llDashboard = findViewById(R.id.ll_dashboard)
        llHdash = findViewById(R.id.ll_hdash)
        llBatchSummary = findViewById(R.id.ll_batch_summary)
        pbBatchSize = findViewById(R.id.pb_batch_size)
        tvBatchSizeProgress = findViewById(R.id.tv_batch_size_progress)
        tvMaxBatchSize = findViewById(R.id.tv_max_batch_size)
        pbBatchTime = findViewById(R.id.pb_batch_time)
        tvBatchTimeProgress = findViewById(R.id.tv_batch_time_progress)
        tvMaxBatchTime = findViewById(R.id.tv_max_batch_time)
        tvDashcardLocation = findViewById(R.id.tv_dashcard_location)
        tvDashcardSku = findViewById(R.id.tv_dashcard_sku)
        tvDashcardQuantity = findViewById(R.id.tv_dashcard_quantity)
        btnStartNewRecord = findViewById(R.id.btn_start_new_record)
        btnMenuContinueBatch = findViewById(R.id.btn_menu_continue_batch)
        btnEditRecords = findViewById(R.id.btn_edit_records)
        btnExportRecords = findViewById(R.id.btn_export_records)
        btnExportClearRecords = findViewById(R.id.btn_export_clear)
        btnImportRecords = findViewById(R.id.btn_import_records)
        btnManageUsers = findViewById(R.id.btn_manage_users)
        btnSettings = findViewById(R.id.btn_settings)
        btnTransferData = findViewById(R.id.btn_transfer_data)
        btnBatchList = findViewById(R.id.btn_batch_list)
        tvLoggedInUser = findViewById(R.id.tv_logged_in_user)
    }

    // --- SAF Launchers ---

    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val recordsToExport = stockEntryFileHandler.loadRecords()
                if (writeCsvToUri(uri, recordsToExport)) {
                    Toast.makeText(this, "Records exported successfully!", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Failed to export records.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val exportAndClearLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri ->
                val recordsToExport = stockEntryFileHandler.loadRecords()

                // Export original, unenriched data first
                val success = writeCsvToUri(uri, recordsToExport)

                if (success) {
                    val actionUser = GoStockApp.loggedInUser?.username ?: "Unknown"
                    val actionTimestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())

                    // Now, convert the exported records to the Archived type and enrich them
                    val archivedRecords = recordsToExport.map {
                        StockEntryArchived(
                            id = it.id,
                            timestamp = it.timestamp,
                            username = it.username,
                            locationBarcode = it.locationBarcode,
                            skuBarcode = it.skuBarcode,
                            quantity = it.quantity,
                            action_user = actionUser,
                            action_timestamp = System.currentTimeMillis(),
                            action = "Entry Exported and Cleared"
                        )
                    }

                    // Save the archived records to the deleted file
                    stockEntryArchivedFileHandler.addMultipleRecords(archivedRecords)
                    // Clear the original data
                    stockEntryFileHandler.clearData()

                    Toast.makeText(this, "Records exported and cleared!", Toast.LENGTH_LONG).show()
                    updateDashboard()
                } else {
                    Toast.makeText(this, "Failed to export records. Data was not cleared.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private val importDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { importRecordsFromCsv(it) }
        }
    }

    // --- Click Listeners and UI Setup ---

    private fun setupClickListeners() {
        btnStartNewRecord.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        btnMenuContinueBatch.setOnClickListener { startActivity(Intent(this, MainActivity::class.java)) }
        btnEditRecords.setOnClickListener { startActivity(Intent(this, RecordListActivity::class.java)) }
        btnExportRecords.setOnClickListener { initiateSafExport(ExportType.EXPORT_ONLY) }
        btnExportClearRecords.setOnClickListener { initiateSafExport(ExportType.EXPORT_AND_CLEAR) }
        btnImportRecords.setOnClickListener {
            val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "*/*"
            }
            importDocumentLauncher.launch(intent)
        }
        btnTransferData.setOnClickListener { startActivity(Intent(this, TransferDataActivity::class.java)) }
        btnBatchList.setOnClickListener { startActivity(Intent(this, BatchListActivity::class.java)) }
        btnManageUsers.setOnClickListener { startActivity(Intent(this, UserManagementActivity::class.java)) }
        btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
    }

    private fun setupUserDetails() {
        GoStockApp.loggedInUser?.let {
            tvLoggedInUser.text = it.username
            tvLoggedInUser.setOnClickListener { view -> showUserMenu(view) }
        }
    }

    private fun setupRoleBasedVisibility() {
        if (GoStockApp.loggedInUser?.role == UserRole.ADMIN) {
            listOf(btnExportRecords, btnExportClearRecords, btnImportRecords, btnBatchList, btnSettings, btnManageUsers).forEach { it.visibility = View.VISIBLE }
        } else {
            listOf(btnExportRecords, btnExportClearRecords, btnImportRecords, btnBatchList, btnSettings, btnManageUsers).forEach { it.visibility = View.GONE }
        }
    }

    // --- Core Logic Functions ---

    private fun initiateSafExport(exportType: ExportType) {
        if (stockEntryFileHandler.loadRecords().isEmpty()) {
            Toast.makeText(this, "No records to export!", Toast.LENGTH_SHORT).show()
            return
        }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = if (exportType == ExportType.EXPORT_AND_CLEAR) "stock_records_cleared_$timestamp.csv" else "stock_records_$timestamp.csv"

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }

        if (exportType == ExportType.EXPORT_ONLY) {
            createDocumentLauncher.launch(intent)
        } else {
            exportAndClearLauncher.launch(intent)
        }
    }

    private fun writeCsvToUri(uri: Uri, records: List<StockEntry>): Boolean {
        val csvBuilder = StringBuilder()
        csvBuilder.append("ID,Timestamp,Username,LocationBarcode,SkuBarcode,Quantity\n")
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())

        for (record in records) {
            val formattedTimestamp = sdf.format(Date(record.timestamp))
            csvBuilder.append("${escapeCsv(record.id)},")
            csvBuilder.append("${escapeCsv(formattedTimestamp)},")
            csvBuilder.append("${escapeCsv(record.username)},")
            csvBuilder.append("${escapeCsv(record.locationBarcode)},")
            csvBuilder.append("${escapeCsv(record.skuBarcode)},")
            csvBuilder.append("${record.quantity}\n")
        }

        return try {
            contentResolver.openOutputStream(uri)?.use { it.write(csvBuilder.toString().toByteArray()) }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    private fun importRecordsFromCsv(uri: Uri) {
        val importedEntries = mutableListOf<StockEntry>()
        try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readLine() // Skip header
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val columns = parseCsvLine(line!!)
                        if (columns.size >= 6) {
                            try {
                                val entry = StockEntry(
                                    id = columns[0],
                                    timestamp = parseTimestamp(columns[1]),
                                    username = columns[2],
                                    locationBarcode = columns[3],
                                    skuBarcode = columns[4],
                                    quantity = columns[5].toInt()
                                )
                                importedEntries.add(entry)
                            } catch (e: Exception) { /* Skip malformed row */ }
                        }
                    }
                }
            }
            if (importedEntries.isNotEmpty()) {
                stockEntryFileHandler.addMultipleRecords(importedEntries)
                Toast.makeText(this, "Successfully imported ${importedEntries.size} records!", Toast.LENGTH_LONG).show()
                updateDashboard()
            } else {
                Toast.makeText(this, "No valid records found to import.", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error reading CSV file.", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateDashboard() {
        val allEntries = stockEntryFileHandler.loadRecords()
        if (allEntries.isEmpty()) {
            tvWelcomeSmallMessage.visibility = View.GONE
            llDashboard.visibility = View.GONE
            llHdash.visibility = View.GONE
            btnMenuContinueBatch.visibility = View.GONE
            btnStartNewRecord.visibility = View.VISIBLE
            findViewById<TextView>(R.id.tv_batch_status).text = "⚪ No batch started yet."
        } else {
            tvWelcomeSmallMessage.visibility = View.VISIBLE
            llDashboard.visibility = View.VISIBLE
            llHdash.visibility = View.VISIBLE
            btnMenuContinueBatch.visibility = View.VISIBLE
            btnStartNewRecord.visibility = View.GONE

            val currentRecordCount = allEntries.size
            val maxBatchSize = AppSettings.maxBatchSize
            if (maxBatchSize > 0) {
                tvBatchSizeProgress.text = "$currentRecordCount"
                tvMaxBatchSize.text = "/ $maxBatchSize"
                pbBatchSize.progress = ((currentRecordCount.toFloat() / maxBatchSize) * 100).toInt().coerceIn(0, 100)
            } else {
                tvBatchSizeProgress.text = "$currentRecordCount"
                tvMaxBatchSize.text = "/ ∞"
                pbBatchSize.progress = 0
            }

            val oldestEntryTimestamp = allEntries.minOfOrNull { it.timestamp }

            if (oldestEntryTimestamp != null) {
                val elapsedTimeHours = TimeUnit.MILLISECONDS.toHours(System.currentTimeMillis() - oldestEntryTimestamp)
                val maxBatchTimeHours = AppSettings.maxBatchTime
                if (maxBatchTimeHours > 0) {
                    tvBatchTimeProgress.text = "${elapsedTimeHours}h"
                    tvMaxBatchTime.text = "/ ${maxBatchTimeHours}h"
                    pbBatchTime.progress = ((elapsedTimeHours.toFloat() / maxBatchTimeHours) * 100).toInt().coerceIn(0, 100)
                } else {
                    tvBatchTimeProgress.text = "${elapsedTimeHours}h"
                    tvMaxBatchTime.text = "/ ∞"
                    pbBatchTime.progress = 0
                }
                val formattedTimestamp = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(oldestEntryTimestamp))
                findViewById<TextView>(R.id.tv_batch_status).text = "🟢 Batch started on $formattedTimestamp"
            } else {
                findViewById<TextView>(R.id.tv_batch_status).text = "⚪ No batch started yet."
            }

            tvDashcardLocation.text = allEntries.map { it.locationBarcode }.distinct().size.toString()
            tvDashcardSku.text = allEntries.map { it.skuBarcode }.distinct().size.toString()
            tvDashcardQuantity.text = allEntries.sumOf { it.quantity }.toString()
        }

        GoStockApp.loggedInUser?.let {
            tvWelcomeMessage.text = "👋🏾 Hello ${it.username}!"
        }
    }

    // --- Helper Functions ---
    private fun parseTimestamp(dateString: String): Long {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return try {
            val date = sdf.parse(dateString)
            date?.time ?: 0L
        } catch (e: Exception) {
            e.printStackTrace()
            0L
        }
    }

    private fun escapeCsv(field: String): String = if (field.contains(",") || field.contains("\"") || field.contains("\n")) "\"${field.replace("\"", "\"\"")}\"" else field

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var currentPos = 0
        var inQuotes = false
        var fieldStart = 0
        while (currentPos < line.length) {
            when (line[currentPos]) {
                '"' -> inQuotes = !inQuotes
                ',' -> if (!inQuotes) {
                    result.add(line.substring(fieldStart, currentPos).replace("\"\"", "\"").removeSurrounding("\""))
                    fieldStart = currentPos + 1
                }
            }
            currentPos++
        }
        result.add(line.substring(fieldStart).replace("\"\"", "\"").removeSurrounding("\""))
        return result
    }

    private fun showUserMenu(view: View) {
        val popup = PopupMenu(this, view, Gravity.END)
        popup.menuInflater.inflate(R.menu.user_menu, popup.menu)
        val themeToggleMenuItem = popup.menu.findItem(R.id.action_toggle_theme)
        themeToggleMenuItem.title = if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) "Switch to Light Mode" else "Switch to Dark Mode"
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_change_password -> { startActivity(Intent(this, ChangePasswordActivity::class.java)); true }
                R.id.action_toggle_theme -> { toggleTheme(); true }
                R.id.action_logout -> { performLogout(); true }
                else -> false
            }
        }
        popup.show()
    }

    private fun performLogout() {
        (application as GoStockApp).clearLoginSession()
        val intent = Intent(this, LoginActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        startActivity(intent)
        finish()
    }

    private fun toggleTheme() {
        val currentNightMode = AppCompatDelegate.getDefaultNightMode()
        val newNightMode = if (currentNightMode == AppCompatDelegate.MODE_NIGHT_YES) AppCompatDelegate.MODE_NIGHT_NO else AppCompatDelegate.MODE_NIGHT_YES
        AppCompatDelegate.setDefaultNightMode(newNightMode)
        getSharedPreferences(GoStockApp.PREFS_FILE_NAME, Context.MODE_PRIVATE).edit { putInt(GoStockApp.KEY_THEME_MODE, newNightMode) }
    }
}
