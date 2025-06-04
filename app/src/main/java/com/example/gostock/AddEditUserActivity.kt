package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.os.Bundle
// import android.util.Log // Log import removed for cleaner output
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class AddEditUserActivity : AppCompatActivity() {

    private lateinit var tvTitle: TextView
    private lateinit var etFirstName: EditText
    private lateinit var etLastName: EditText
    private lateinit var etUsername: EditText
    private lateinit var tvPasswordLabel: TextView
    private lateinit var etPassword: EditText
    private lateinit var etConfirmPassword: EditText
    private lateinit var spinnerRole: Spinner
    private lateinit var btnToolbarSave: ImageButton
    private lateinit var btnToolbarBack: ImageButton
    private lateinit var llPassword: LinearLayout
    private lateinit var btnDeleteRecord: LinearLayout

    private lateinit var userFileHandler: UserFileHandler
    private var isEditing: Boolean = false
    private var currentUser: User? = null // Null if adding, non-null if editing

    // private val TAG = "AddEditUserActivity" // For logging, removed for cleaner output

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_edit_user)
        // Log.d(TAG, "onCreate: Activity started.") // Debug log removed

        // Basic admin check (should already be handled by UserManagementActivity, but good for safety)
        if (GoStockApp.loggedInUser?.role != UserRole.ADMIN) {
            Toast.makeText(this, "Access Denied: Admin privileges required.", Toast.LENGTH_LONG).show()
            // Log.w(TAG, "Access denied: Non-admin tried to access AddEditUserActivity.") // Debug log removed
            finish()
            return
        }

        tvTitle = findViewById(R.id.page_title)
        etFirstName = findViewById(R.id.et_first_name)
        etLastName = findViewById(R.id.et_last_name)
        etUsername = findViewById(R.id.et_username)
        tvPasswordLabel = findViewById(R.id.tv_password_label)
        etPassword = findViewById(R.id.et_password)
        etConfirmPassword = findViewById(R.id.et_confirm_password)
        spinnerRole = findViewById(R.id.spinner_role)
        btnToolbarSave = findViewById(R.id.btn_toolbar_save)
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)

        userFileHandler = UserFileHandler(this)

        llPassword = findViewById(R.id.ll_password)

        btnDeleteRecord = findViewById(R.id.btn_delete_record)

        setupRoleSpinner()

        val userId = intent.getStringExtra(UserManagementActivity.EXTRA_USER_ID)
        if (userId != null) {
            isEditing = true
            tvTitle.text = "Edit User"
            llPassword.visibility = View.GONE
            btnDeleteRecord.visibility = View.VISIBLE
            // Log.d(TAG, "onCreate: Editing existing user with ID: $userId") // Debug log removed
            loadUserDataForEditing(userId)
        } else {
            isEditing = false
            tvTitle.text = "New User"
            llPassword.visibility = View.VISIBLE
            btnDeleteRecord.visibility = View.GONE
            // Log.d(TAG, "onCreate: Adding new user.") // Debug log removed
        }

        setupClickListeners()
    }

    private fun setupRoleSpinner() {
        val roles = UserRole.values().map { it.name }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, roles)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerRole.adapter = adapter
        // Log.d(TAG, "setupRoleSpinner: Roles populated.") // Debug log removed
    }

    private fun loadUserDataForEditing(userId: String) {
        val users = userFileHandler.loadUsers()
        currentUser = users.find { it.id == userId }

        currentUser?.let { user ->
            etFirstName.setText(user.firstName)
            etLastName.setText(user.lastName)
            etUsername.setText(user.username)

            // Determine if the current logged-in user is editing their own account
            val loggedInUserId = GoStockApp.loggedInUser?.id
            val editingUserId = user.id
            val isEditingSelf = loggedInUserId == editingUserId // Direct comparison

            // Debugging logs from previous round:
            // Log.d(TAG, "loadUserDataForEditing: --- Debugging isEditingSelf ---")
            // Log.d(TAG, "loadUserDataForEditing: User being edited: '${user.username}' (ID: $editingUserId)")
            // Log.d(TAG, "loadUserDataForEditing: Currently logged in user: '${GoStockApp.loggedInUser?.username}' (ID: $loggedInUserId)")
            // Log.d(TAG, "loadUserDataForEditing: Result of GoStockApp.loggedInUser?.id == user.id is: $isEditingSelf")
            // Log.d(TAG, "loadUserDataForEditing: ------------------------------")


            // Rule 1: Username is editable for other users, but NOT for self
            etUsername.isEnabled = !isEditingSelf
            // Log.d(TAG, "Username field enabled set to: ${etUsername.isEnabled}") // Debug log removed


            // Rule 2: Role is editable for other users, but NOT for self
            spinnerRole.isEnabled = !isEditingSelf
            // Log.d(TAG, "Role spinner enabled set to: ${spinnerRole.isEnabled}") // Debug log removed


            // CHANGE HERE: Show password fields if editing another user, hide if editing self
            if (isEditingSelf) {
                tvPasswordLabel.visibility = View.GONE
                etPassword.visibility = View.GONE
                etConfirmPassword.visibility = View.GONE
            } else {
                tvPasswordLabel.visibility = View.VISIBLE
                etPassword.visibility = View.VISIBLE
                etPassword.hint = "Enter new password (optional)" // Change hint
                etConfirmPassword.visibility = View.VISIBLE
            }
            // Set selected role in spinner
            val roleIndex = UserRole.values().indexOf(user.role)
            if (roleIndex != -1) {
                spinnerRole.setSelection(roleIndex)
            }

        } ?: run {
            Toast.makeText(this, "User not found for editing.", Toast.LENGTH_LONG).show()
            // Log.e(TAG, "loadUserDataForEditing: User with ID $userId not found.") // Debug log removed
            finish()
        }
    }

    private fun setupClickListeners() {
        btnToolbarSave.setOnClickListener {
            // Log.d(TAG, "Save User button clicked.") // Debug log removed
            saveUser()
        }

        btnToolbarBack.setOnClickListener {
            // Log.d(TAG, "Cancel User button clicked.") // Debug log removed
            setResult(RESULT_CANCELED)
            finish()
        }

        btnDeleteRecord.setOnClickListener {
            confirmDelete()
        }
    }

    private fun saveUser() {
        val firstName = etFirstName.text.toString().trim()
        val lastName = etLastName.text.toString().trim()
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString()
        val confirmPassword = etConfirmPassword.text.toString()
        val selectedRole = UserRole.valueOf(spinnerRole.selectedItem.toString())

        // Input validation
        if (firstName.isEmpty() || lastName.isEmpty() || username.isEmpty()) {
            Toast.makeText(this, "First Name, Last Name, and Username cannot be empty.", Toast.LENGTH_SHORT).show()
            // Log.w(TAG, "Validation failed: Empty fields.") // Debug log removed
            return
        }

        val users = userFileHandler.loadUsers()

        // Check for duplicate username (important for both adding and editing if username is changed)
        val duplicateUser = users.find { it.username.equals(username, ignoreCase = true) && it.id != currentUser?.id }
        if (duplicateUser != null) {
            Toast.makeText(this, "Username '$username' already exists. Please choose a different one.", Toast.LENGTH_LONG).show()
            // Log.w(TAG, "Validation failed: Duplicate username '$username'.") // Debug log removed
            return
        }

        val userToSave: User
        if (!isEditing) { // Logic for ADDING new user
            // Log.d(TAG, "saveUser: Adding new user.") // Debug log removed
            if (password.isEmpty() || password.length < 6) {
                Toast.makeText(this, "Password is required for new users and must be at least 6 characters.", Toast.LENGTH_SHORT).show()
                // Log.w(TAG, "Validation failed: New user password invalid.") // Debug log removed
                return
            }
            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                // Log.w(TAG, "Validation failed: Passwords do not match.") // Debug log removed
                return
            }

            userToSave = User(
                firstName = firstName,
                lastName = lastName,
                username = username,
                passwordHash = PasswordHasher.hashPassword(password),
                role = selectedRole
            )
            userFileHandler.addUser(userToSave)
            Toast.makeText(this, "User '${username}' added successfully!", Toast.LENGTH_SHORT).show()
            // Log.d(TAG, "User '${username}' added successfully.") // Debug log removed

        } else { // Logic for EDITING existing user
            // Log.d(TAG, "saveUser: Editing existing user: ${currentUser?.username}.") // Debug log removed
            val originalUser = currentUser!! // Guaranteed to be not null if isEditing is true

            // Determine password hash: If new password provided, hash it, else keep old.
            val newPasswordHash = if (password.isNotEmpty()) {
                // Log.d(TAG, "saveUser: New password provided for edit.") // Debug log removed
                if (password.length < 6) {
                    Toast.makeText(this, "New password must be at least 6 characters.", Toast.LENGTH_SHORT).show() // Log.w(TAG, "Validation failed: New password too short."); // Debug log removed
                    return
                }
                if (password != confirmPassword) {
                    Toast.makeText(this, "New passwords do not match.", Toast.LENGTH_SHORT).show() // Log.w(TAG, "Validation failed: New passwords do not match."); // Debug log removed
                    return
                }
                PasswordHasher.hashPassword(password)
            } else {
                // Log.d(TAG, "saveUser: No new password provided, keeping old hash.") // Debug log removed
                originalUser.passwordHash // Keep old password hash if new one is empty
            }

            userToSave = originalUser.copy(
                firstName = firstName,
                lastName = lastName,
                username = username, // Username can now be updated if etUsername.isEnabled was true (handled by loadUserDataForEditing)
                passwordHash = newPasswordHash,
                role = selectedRole
            )
            userFileHandler.updateUser(userToSave)
            Toast.makeText(this, "User '${userToSave.username}' updated successfully!", Toast.LENGTH_SHORT).show()
            // Log.d(TAG, "User '${userToSave.username}' updated successfully.") // Debug log removed

            // Special handling if the currently logged-in user's account was modified
            if (GoStockApp.loggedInUser?.id == userToSave.id) {
                // Log.d(TAG, "saveUser: Logged-in user's own account updated. Re-saving session.") // Debug log removed
                (application as GoStockApp).saveLoginSession(userToSave) // Re-save session in case name/role changed
                // Inform user if their own role was changed by another admin
                if (userToSave.role != originalUser.role) {
                    Toast.makeText(this, "Your account details updated. Please log out and log in again for changes to take full effect.", Toast.LENGTH_LONG).show()
                }
            }
        }
        setResult(RESULT_OK) // Indicate success to UserManagementActivity
        finish()
        // Log.d(TAG, "saveUser: Activity finished with RESULT_OK.") // Debug log removed
    }

    private fun confirmDelete() {
        if (GoStockApp.loggedInUser?.id == currentUser?.id) {
            Toast.makeText(this, "Cannot delete current user.", Toast.LENGTH_LONG).show()
            return
        }
        
        AlertDialog.Builder(this)
            .setTitle("Delete User")
            .setMessage("Are you sure you want to permanently delete this user? This action cannot be undone.")
            .setPositiveButton("Yes") { dialog, which ->
                currentUser?.id?.let { id ->
                    userFileHandler.deleteUser(id) // Use FileHandler to delete
                    setResult(RESULT_OK) // Indicate success (record was deleted)
                    Toast.makeText(this, "User deleted successfully!", Toast.LENGTH_SHORT).show()
                    finish() // Close activity
                }
            }
            .setNegativeButton("No", null)
            .show()
    }
}