package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import android.util.Log

import androidx.appcompat.app.AppCompatDelegate

class GoStockApp : Application() {

    companion object {
        var loggedInUser: User? = null
        const val PREFS_FILE_NAME = "user_prefs"
        private const val KEY_LOGGED_IN_USERNAME = "logged_in_username"
        const val KEY_THEME_MODE = "theme_mode"

        // We no longer need to store role separately as we'll load the full user object
        // private const val KEY_LOGGED_IN_ROLE = "logged_in_role"
    }

    private lateinit var userFileHandler: UserFileHandler
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate() {
        super.onCreate()
        Log.d("GoStockApp", "Application onCreate called.")

        AppSettings.initialize(this)

        userFileHandler = UserFileHandler(this)
        sharedPreferences = getSharedPreferences(PREFS_FILE_NAME, Context.MODE_PRIVATE)

        // --- Apply saved theme preference early in onCreate ---
        val savedThemeMode = sharedPreferences.getInt(KEY_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        AppCompatDelegate.setDefaultNightMode(savedThemeMode)
        // ---------------------------------------------------

        // --- One-time setup: Create default admin user if no users exist ---
        createDefaultAdminUserIfNeeded()

        // --- Restore logged-in user from SharedPreferences on app start ---
        restoreLoginSession()
    }

    private fun createDefaultAdminUserIfNeeded() {
        val existingUsers = userFileHandler.loadUsers()
        if (existingUsers.isEmpty()) {
            Log.d("GoStockApp", "No users found. Creating default admin user.")
            val adminUser = User(
                username = "admin",
                passwordHash = PasswordHasher.hashPassword("admin"), // Hash 'admin' password
                firstName = "System",
                lastName = "Admin",
                role = UserRole.ADMIN
            )
            userFileHandler.addUser(adminUser)
            Log.d("GoStockApp", "Default admin user created successfully.")
        } else {
            Log.d("GoStockApp", "Existing users found (${existingUsers.size}). No need to create default admin.")
        }
    }

    private fun restoreLoginSession() {
        val username = sharedPreferences.getString(KEY_LOGGED_IN_USERNAME, null)
        // No longer restoring role directly, will get from full user object

        if (username != null) {
            // Load the full user object from file using the username
            val userFromStorage = userFileHandler.loadUsers().find { it.username.equals(username, ignoreCase = true) }

            if (userFromStorage != null) {
                loggedInUser = userFromStorage // Set the actual user object with its correct ID
                Log.d("GoStockApp", "Restored login session for user: ${loggedInUser?.username} (ID: ${loggedInUser?.id})")
            } else {
                Log.e("GoStockApp", "User '$username' found in prefs but not in file. Clearing session.")
                clearLoginSession() // User not found in file, clear invalid session
            }
        } else {
            Log.d("GoStockApp", "No active login session found in SharedPreferences.")
        }
    }

    fun saveLoginSession(user: User) {
        sharedPreferences.edit().apply {
            putString(KEY_LOGGED_IN_USERNAME, user.username)
            // No longer storing role directly, as we load the full user object
            apply() // Apply changes asynchronously
        }
        loggedInUser = user // Also update the global loggedInUser (with its correct ID)
        Log.d("GoStockApp", "Saved login session for user: ${user.username} (ID: ${user.id})")
    }

    fun clearLoginSession() {
        sharedPreferences.edit().apply {
            remove(KEY_LOGGED_IN_USERNAME)
            // remove(KEY_LOGGED_IN_ROLE) // No longer needed
            apply()
        }
        loggedInUser = null
        Log.d("GoStockApp", "Login session cleared.")
    }
}