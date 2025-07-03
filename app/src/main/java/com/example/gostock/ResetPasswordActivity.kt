package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmNewPassword: EditText
    private lateinit var btnToolbarSave: ImageButton
    private lateinit var btnToolbarBack: ImageButton

    private lateinit var userFileHandler: UserFileHandler
    private var userToReset: User? = null // This will hold the user whose password is being reset

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reset_password)

        etNewPassword = findViewById(R.id.et_new_password)
        etConfirmNewPassword = findViewById(R.id.et_confirm_new_password)
        btnToolbarSave = findViewById(R.id.btn_toolbar_save)
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)

        userFileHandler = UserFileHandler(this)

        // --- FIX: Get the target user's ID from the intent ---
        val userIdToReset = intent.getStringExtra(UserManagementActivity.EXTRA_USER_ID)
        if (userIdToReset == null) {
            Toast.makeText(this, "Error: No user specified for password reset.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        // Load the specific user to be edited
        userToReset = userFileHandler.loadUsers().find { it.id == userIdToReset }

        if (userToReset == null) {
            Toast.makeText(this, "Error: Could not find the user to reset.", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        setupClickListeners()
    }

    private fun setupClickListeners() {
        btnToolbarSave.setOnClickListener {
            saveNewPassword()
        }
        btnToolbarBack.setOnClickListener {
            finish() // Just close the activity
        }
    }

    private fun saveNewPassword() {
        val newPassword = etNewPassword.text.toString()
        val confirmNewPassword = etConfirmNewPassword.text.toString()

        // Use the userToReset object, which is guaranteed to be non-null here
        val targetUser = userToReset ?: return

        // 1. Validate new password
        if (newPassword.isEmpty() || newPassword.length < 6) {
            Toast.makeText(this, "New password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
            return
        }
        if (newPassword != confirmNewPassword) {
            Toast.makeText(this, "New passwords do not match.", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Update the correct user's password
        val newPasswordHash = PasswordHasher.hashPassword(newPassword)
        val updatedUser = targetUser.copy(passwordHash = newPasswordHash)

        userFileHandler.updateUser(updatedUser) // Save the updated user to the file

        // --- DO NOT update the login session ---
        // (application as GoStockApp).saveLoginSession(updatedUser) // This line was incorrect

        Toast.makeText(this, "Password for '${targetUser.username}' reset successfully!", Toast.LENGTH_SHORT).show()
        setResult(Activity.RESULT_OK) // Set result so the user list refreshes
        finish() // Close activity
    }
}
