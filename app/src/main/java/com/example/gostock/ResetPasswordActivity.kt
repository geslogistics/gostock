package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.content.Intent
import android.os.Bundle
import android.widget.ImageButton
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ResetPasswordActivity : AppCompatActivity() {

    private lateinit var etNewPassword: EditText
    private lateinit var etConfirmNewPassword: EditText
    private lateinit var btnToolbarSave: ImageButton
    private lateinit var btnToolbarBack: ImageButton

    private lateinit var userFileHandler: UserFileHandler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        etNewPassword = findViewById(R.id.et_new_password)
        etConfirmNewPassword = findViewById(R.id.et_confirm_new_password)
        btnToolbarSave = findViewById(R.id.btn_toolbar_save)
        btnToolbarBack = findViewById(R.id.btn_toolbar_back)

        userFileHandler = UserFileHandler(this)

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

        // 1. Validate new password
        if (newPassword.isEmpty() || newPassword.length < 6) {
            Toast.makeText(this, "New password must be at least 6 characters.", Toast.LENGTH_SHORT).show()
            return
        }
        if (newPassword != confirmNewPassword) {
            Toast.makeText(this, "New passwords do not match.", Toast.LENGTH_SHORT).show()
            return
        }

        // 2. Update user password
        val newPasswordHash = PasswordHasher.hashPassword(newPassword)
        val updatedUser = loggedInUser.copy(passwordHash = newPasswordHash)

        userFileHandler.updateUser(updatedUser) // Save to file
        (application as GoStockApp).saveLoginSession(updatedUser) // Update session object

        Toast.makeText(this, "Password reset successfully!", Toast.LENGTH_SHORT).show()
        finish() // Close activity
    }
}