package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Imports for SAF
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import java.io.OutputStream

import android.view.Gravity // Add this for PopupMenu positioning
import androidx.appcompat.widget.Toolbar // Add this
import androidx.appcompat.widget.PopupMenu // Add this
import android.widget.TextView // Add this
import android.view.View // Add this line

import androidx.appcompat.app.AppCompatDelegate

import android.content.Context


class HomeActivity : AppCompatActivity() {

    private lateinit var btnNewStockTake: Button
    private lateinit var btnEditRecords: Button
    private lateinit var btnExportRecords: Button

    private lateinit var fileHandler: FileHandler

    private lateinit var btnManageUsers: Button // New button declaration
    private lateinit var tvLoggedInUser: TextView // Declare TextView for user display
    private lateinit var toolbar: Toolbar // Declare Toolbar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        btnNewStockTake = findViewById(R.id.btn_new_stock_take)
        btnEditRecords = findViewById(R.id.btn_edit_records)
        btnExportRecords = findViewById(R.id.btn_export_records)

        fileHandler = FileHandler(this)

        btnManageUsers = findViewById(R.id.btn_manage_users) // Link the new button

        tvLoggedInUser = findViewById(R.id.tv_logged_in_user) // Link the user TextView
        toolbar = findViewById(R.id.toolbar_home) // Link the Toolbar

        setSupportActionBar(toolbar) // Set the toolbar as the activity's action bar
        supportActionBar?.setDisplayShowTitleEnabled(false) // Hide default title


        setupUserDetails() // New function to set user details
        setupClickListeners()
        setupRoleBasedVisibility() // New function for button visibility
    }

    private fun setupUserDetails() {
        val loggedInUser = GoStockApp.loggedInUser
        if (loggedInUser != null) {
            tvLoggedInUser.text = "${loggedInUser.username}"
            tvLoggedInUser.setOnClickListener { view ->
                showUserMenu(view)
            }
        } else {
            // Should not happen if login flow is correct, but as a fallback
            tvLoggedInUser.text = "Not Logged In"
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

        // Dynamically set the text for the theme toggle menu item
        val themeToggleMenuItem = popup.menu.findItem(R.id.action_toggle_theme)
        if (AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES) {
            themeToggleMenuItem.title = "Switch to Light Mode"
        } else {
            themeToggleMenuItem.title = "Switch to Dark Mode"
        }

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_toggle_theme -> { // Handle the new theme toggle
                    toggleTheme()
                    true
                }
                R.id.action_change_password -> { // Handle new menu item
                    val intent = Intent(this, ChangePasswordActivity::class.java)
                    startActivity(intent)
                    true // Consume the click
                }
                R.id.action_logout -> {
                    performLogout()
                    true // Consume the click
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun toggleTheme() {
        val currentNightMode = AppCompatDelegate.getDefaultNightMode()
        val newNightMode = if (currentNightMode == AppCompatDelegate.MODE_NIGHT_YES) {
            AppCompatDelegate.MODE_NIGHT_NO // Switch to Light
        } else {
            AppCompatDelegate.MODE_NIGHT_YES // Switch to Dark
        }

        // Apply the new theme
        AppCompatDelegate.setDefaultNightMode(newNightMode)

        // Save the preference
        val sharedPrefs = getSharedPreferences(GoStockApp.PREFS_FILE_NAME, Context.MODE_PRIVATE)
        sharedPrefs.edit().putInt(GoStockApp.KEY_THEME_MODE, newNightMode).apply()

        Toast.makeText(this, "Theme switched!", Toast.LENGTH_SHORT).show()
        // No need to restart activity, setDefaultNightMode automatically recreates activities with new theme
    }

    private fun performLogout() {
        (application as GoStockApp).clearLoginSession() // Clear session data
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // Clear back stack
        startActivity(intent)
        finish() // Finish HomeActivity
        Toast.makeText(this, "Logged out successfully!", Toast.LENGTH_SHORT).show()
    }

    private fun setupClickListeners() {
        btnNewStockTake.setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        }

        btnEditRecords.setOnClickListener {
            val intent = Intent(this, RecordListActivity::class.java)
            startActivity(intent)
        }

        btnExportRecords.setOnClickListener {
            // Trigger SAF to create a file
            createCsvFileWithSaf()
        }

        btnManageUsers.setOnClickListener {
            val intent = Intent(this, UserManagementActivity::class.java)
            startActivity(intent)
        }
    }

    // SAF Activity Result Launcher for creating a file
    private val createDocumentLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri? = result.data?.data
            uri?.let { fileUri ->
                exportRecordsToCsv(fileUri)
            } ?: run {
                Toast.makeText(this, "File creation cancelled or failed.", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "File creation cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    // Step 1 for SAF: Open file picker to choose save location/name
    private fun createCsvFileWithSaf() {
        val records = fileHandler.loadStockEntries()

        if (records.isEmpty()) {
            Toast.makeText(this, "No records to export!", Toast.LENGTH_SHORT).show()
            return
        }

        val csvFileName = "stock_records_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())}.csv"

        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/csv" // MIME type for CSV
            putExtra(Intent.EXTRA_TITLE, csvFileName)
            // Optionally, you can set a default directory, but it's not strictly necessary.
            // putExtra(DocumentsContract.EXTRA_INITIAL_URI, DocumentsContract.buildDocumentUri(
            //     DocumentsContract.PUBLIC_DOWNLOADS_PROVIDER_AUTHORITY, "downloads"))
        }
        createDocumentLauncher.launch(intent)
    }


    // Step 2 for SAF: Write data to the selected URI
    private fun exportRecordsToCsv(uri: Uri) {
        val records = fileHandler.loadStockEntries()

        if (records.isEmpty()) {
            Toast.makeText(this, "No records to export!", Toast.LENGTH_SHORT).show()
            return
        }

        val csvBuilder = StringBuilder()
        // Add header row
        csvBuilder.append("ID,Timestamp,Username,LocationBarcode,SkuBarcode,Quantity\n")

        // Add data rows
        for (record in records) {
            csvBuilder.append("${escapeCsv(record.id)},")
            csvBuilder.append("${escapeCsv(record.timestamp)},")
            csvBuilder.append("${escapeCsv(record.username)},")
            csvBuilder.append("${escapeCsv(record.locationBarcode)},")
            csvBuilder.append("${escapeCsv(record.skuBarcode)},")
            csvBuilder.append("${record.quantity}\n")
        }

        try {
            contentResolver.openOutputStream(uri)?.use { outputStream: OutputStream ->
                outputStream.write(csvBuilder.toString().toByteArray())
                Toast.makeText(this, "Records exported successfully!", Toast.LENGTH_LONG).show()

                // Optional: Offer to open the CSV file immediately after saving
                openCsvFile(uri)
            } ?: run {
                Toast.makeText(this, "Failed to open output stream.", Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error exporting data: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // Helper function to escape commas and quotes in CSV fields
    private fun escapeCsv(field: String): String {
        return if (field.contains(",") || field.contains("\"") || field.contains("\n")) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }
    }

    // Optional: Function to open the CSV file after it's been created
    private fun openCsvFile(uri: Uri) {
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "text/csv")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // Important for opening external files
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No app found to open CSV files.", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
}