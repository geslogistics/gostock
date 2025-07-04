package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.pm.PackageInfoCompat

class LoginActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: LinearLayout
    private lateinit var userFileHandler: UserFileHandler
    private lateinit var tvAppVersion: TextView // TextView for the app version

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        etUsername = findViewById(R.id.et_username)
        etPassword = findViewById(R.id.et_password)
        btnLogin = findViewById(R.id.btn_login2)
        tvAppVersion = findViewById(R.id.tv_app_version) // Initialize the TextView
        userFileHandler = UserFileHandler(this)

        // Set the app version text
        setAppVersionText()

        // Check if user is already logged in (from previous session)
        if (GoStockApp.loggedInUser != null) {
            // If yes, redirect to HomeActivity immediately
            navigateToHome()
            return // Finish this activity so user can't go back to login
        }

        btnLogin.setOnClickListener {
            performLogin()
        }
    }

    private fun setAppVersionText() {
        try {
            val packageInfo = packageManager.getPackageInfo(packageName, 0)
            val versionName = packageInfo.versionName
            val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)
            tvAppVersion.text = "Version: $versionName ($versionCode)"
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
            tvAppVersion.text = "Version: N/A"
        }
    }

    private fun performLogin() {
        val username = etUsername.text.toString().trim()
        val password = etPassword.text.toString() // Don't trim password as spaces matter

        if (username.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Please enter username and password", Toast.LENGTH_SHORT).show()
            return
        }

        val users = userFileHandler.loadUsers()
        val user = users.find { it.username.equals(username, ignoreCase = true) } // Case-insensitive username match

        if (user != null && PasswordHasher.verifyPassword(password, user.passwordHash)) {
            // Login successful
            Toast.makeText(this, "Login successful for ${user.username}", Toast.LENGTH_SHORT).show()

            // Save login session in GoStockApp and SharedPreferences
            (application as GoStockApp).saveLoginSession(user)

            navigateToHome()
        } else {
            // Login failed
            Toast.makeText(this, "Invalid username or password", Toast.LENGTH_SHORT).show()
            etPassword.text.clear() // Clear password field
        }
    }

    private fun navigateToHome() {
        val intent = Intent(this, HomeActivity::class.java)
        startActivity(intent)
        finish() // Finish LoginActivity so user can't go back to it with back button
    }
}
