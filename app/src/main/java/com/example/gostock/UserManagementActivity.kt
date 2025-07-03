package com.example.gostock

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.PopupMenu

class UserManagementActivity : AppCompatActivity(), OnUserActionListener {

    private lateinit var rvUsers: RecyclerView
    private lateinit var tvNoUsers: TextView
    private lateinit var btnToolbarMore: ImageButton
    private lateinit var btnToolbarBack: ImageButton
    private lateinit var userFileHandler: UserFileHandler
    private lateinit var userAdapter: UserAdapter
    private var users: MutableList<User> = mutableListOf() // Data source for the adapter

    // Constant for the intent extra when opening AddEditUserActivity
    companion object {
        const val EXTRA_USER_ID = "extra_user_id" // Pass ID for editing
        const val REQUEST_CODE_ADD_EDIT_USER = 200
        private const val TAG = "UserManagementActivity"
    }


    private val addEditUserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadAndDisplayUsers() // Reload list if user was added/edited/deleted
            Toast.makeText(this, "User operation successful!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "User operation cancelled.", Toast.LENGTH_SHORT).show()
        }
    }

    private val resetPasswordLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            loadAndDisplayUsers() // Reload list in case user data was updated
            Toast.makeText(this, "Password reset successful!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Password reset cancelled.", Toast.LENGTH_SHORT).show()
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_user_management)

        // Basic admin check (should already be handled by HomeActivity, but good to double-check)
        if (GoStockApp.loggedInUser?.role != UserRole.ADMIN) {
            Toast.makeText(this, "Access Denied: Admin privileges required.", Toast.LENGTH_LONG).show()
            finish() // Close activity if not admin
            return
        }

        rvUsers = findViewById(R.id.rv_users)
        tvNoUsers = findViewById(R.id.tv_no_users)
        btnToolbarMore = findViewById(R.id.btn_toolbar_more)
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)
        userFileHandler = UserFileHandler(this)

        setupRecyclerView()
        setupClickListeners()
        // Users will be loaded in onResume
    }

    override fun onResume() {
        super.onResume()
        loadAndDisplayUsers() // Reload users every time activity comes to foreground
    }

    private fun setupRecyclerView() {
        userAdapter = UserAdapter(users, this) // Pass the mutable list and the listener
        rvUsers.layoutManager = LinearLayoutManager(this)
        rvUsers.adapter = userAdapter
    }

    private fun setupClickListeners() {
        btnToolbarMore.setOnClickListener { view -> showMoreMenu(view) }

        btnToolbarBack.setOnClickListener {
            finish()
        }
    }

    private fun loadAndDisplayUsers() {
        Log.d(TAG, "loadAndDisplayUsers() called.")
        val loadedUsers = userFileHandler.loadUsers()
        Log.d(TAG, "UserFileHandler returned ${loadedUsers.size} users.")

        users = loadedUsers.toMutableList() // Update the internal list of this activity
        userAdapter.updateData(users) // Update the adapter with the fresh data

        if (users.isEmpty()) {
            rvUsers.visibility = View.GONE
            tvNoUsers.visibility = View.VISIBLE
        } else {
            rvUsers.visibility = View.VISIBLE
            tvNoUsers.visibility = View.GONE
        }
    }

    // --- OnUserActionListener implementation ---

    override fun onEditClick(user: User) {
        Log.d(TAG, "User ${user.username} clicked for edit.")
        // Launch AddEditUserActivity to edit the selected user
        val intent = Intent(this, AddEditUserActivity::class.java).apply {
            putExtra(EXTRA_USER_ID, user.id) // Pass only the user ID
        }
        addEditUserLauncher.launch(intent)
    }

    override fun onDeleteClick(userId: String) {
        Log.d(TAG, "Delete requested for user ID: $userId")
        // Prevent deleting the currently logged-in user if they are the admin
        if (GoStockApp.loggedInUser?.id == userId) {
            Toast.makeText(this, "Cannot delete the currently logged-in user.", Toast.LENGTH_LONG).show()
            return // Stop the deletion process
        }

        AlertDialog.Builder(this)
            .setTitle("Delete User")
            .setMessage("Are you sure you want to permanently delete this user?")
            .setPositiveButton("Yes") { dialog, which ->
                userFileHandler.deleteUser(userId)
                userAdapter.removeItem(userId) // Remove from adapter immediately for UI update
                loadAndDisplayUsers() // Reload to re-check 'no users' visibility
                Toast.makeText(this, "User deleted successfully!", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("No", null)
            .show()
    }

    override fun onResetPasswordClick(userId: String) {
        // Prevent admin from resetting their own password here
        if (GoStockApp.loggedInUser?.id == userId) {
            Toast.makeText(this, "Cannot reset your own password from here. Use 'Change Password' from the Home screen menu.", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(this, ResetPasswordActivity::class.java).apply {
            putExtra(EXTRA_USER_ID, userId) // Pass the ID of the user to reset
        }
        resetPasswordLauncher.launch(intent) // Launch the reset password activity
    }

    private fun showMoreMenu(view: View) {
        val popup = PopupMenu(this, view, Gravity.END)
        popup.menuInflater.inflate(R.menu.user_more_menu, popup.menu)

        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_add_user -> {
                    val intent = Intent(this, AddEditUserActivity::class.java)
                    addEditUserLauncher.launch(intent)
                    true
                }
                R.id.action_send_settings -> {
                    val intent = Intent(this, BluetoothSettingsTransferActivity::class.java)
                    startActivity(intent)
                    true
                }
                R.id.action_send_users_replace_all -> {
                    // TODO: Implement this feature
                    Toast.makeText(this, "Send Users (Replace) coming soon!", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.action_send_users_add_only -> {
                    // TODO: Implement this feature
                    Toast.makeText(this, "Send Users (Add New) coming soon!", Toast.LENGTH_SHORT).show()
                    true
                }
                else -> false
            }
        }
        popup.show()
    }
}