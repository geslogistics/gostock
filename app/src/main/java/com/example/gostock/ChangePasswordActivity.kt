package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ChangePasswordActivity : AppCompatActivity() {

    private lateinit var etOldPassword: EditText
    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmNewPassword: EditText
    private lateinit var btnSavePassword: Button
    private lateinit var btnCancelPasswordChange: Button

    private lateinit var userFileHandler: UserFileHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        etOldPassword = findViewById(R.id.et_old_password)
        etNewPassword = findViewById(R.id.et_new_password)
        etConfirmNewPassword = findViewById(R.id.et_confirm_new_password)
        btnSavePassword = findViewById(R.id.btn_save_password)
        btnCancelPasswordChange = findViewById(R.id.btn_cancel_password_change)

        userFileHandler = UserFileHandler(this)

        setupClickListeners()
    }

    private fun setupClickListeners() {
        btnSavePassword.setOnClickListener {
            saveNewPassword()
        }
        btnCancelPasswordChange.setOnClickListener {
            finish() // Just close the activity
        }
    }

    private fun saveNewPassword() {
        val oldPassword = etOldPassword.text.toString()
        val newPassword = etNewPassword.text.toString()
        val confirmNewPassword = etConfirmNewPassword.text.toString()

        val loggedInUser = GoStockApp.loggedInUser
        if (loggedInUser == null) {
            Toast.makeText(this, "No user logged in. Please log in first.", Toast.LENGTH_LONG).show()
            // Force logout and return to login screen
            (application as GoStockApp).clearLoginSession()
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
            return
        }

        // 1. Verify old password
        if (!PasswordHasher.verifyPassword(oldPassword, loggedInUser.passwordHash)) {
            Toast.makeText(this, "Old password is incorrect.", Toast.LENGTH_SHORT).show()
            etOldPassword.text.clear()
            return
        }

        // 2. Validate new password
        if (newPassword.isEmpty() || newPassword.length < 6) {
            Toast.makeText(this, "New password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
            return
        }
        if (newPassword != confirmNewPassword) {
            Toast.makeText(this, "New passwords do not match.", Toast.LENGTH_SHORT).show()
            return
        }

        // 3. Update user password
        val newPasswordHash = PasswordHasher.hashPassword(newPassword)
        val updatedUser = loggedInUser.copy(passwordHash = newPasswordHash)

        userFileHandler.updateUser(updatedUser) // Save to file
        (application as GoStockApp).saveLoginSession(updatedUser) // Update session object

        Toast.makeText(this, "Password changed successfully!", Toast.LENGTH_SHORT).show()
        finish() // Close activity
    }
}