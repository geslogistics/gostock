package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.content.Context
// import android.util.Log // Log import removed for cleaner output
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

class UserFileHandler(private val context: Context) {

    private val FILENAME = "user_data.json"
    // private val TAG = "UserFileHandler" // Tag for logging, removed for cleaner output

    private fun getFile(): File {
        val file = File(context.filesDir, FILENAME)
        // Log.d(TAG, "User file path: ${file.absolutePath}") // Debug log removed
        return file
    }

    /**
     * Loads all users from the JSON file.
     * If the file doesn't exist or is empty, returns an empty list.
     */
    fun loadUsers(): MutableList<User> {
        val file = getFile()
        // Log.d(TAG, "Attempting to load users from: ${file.absolutePath}") // Debug log removed
        // Log.d(TAG, "User file exists: ${file.exists()}, User file length: ${file.length()}") // Debug log removed

        if (!file.exists() || file.length() == 0L) {
            // Log.d(TAG, "User file does not exist or is empty. Returning empty list.") // Debug log removed
            return mutableListOf()
        }

        return try {
            val reader = FileReader(file)
            val listType = object : TypeToken<MutableList<User>>() {}.type
            val loadedList = Gson().fromJson<MutableList<User>>(reader, listType)
            reader.close()
            // Log.d(TAG, "Loaded users: ${loadedList?.size ?: 0} users.") // Debug log removed
            loadedList ?: mutableListOf()
        } catch (e: IOException) {
            // Log.e(TAG, "IOException loading users: ${e.message}") // Debug log removed
            e.printStackTrace()
            mutableListOf()
        } catch (e: Exception) {
            // Log.e(TAG, "General Exception loading users: ${e.message}") // Debug log removed
            e.printStackTrace()
            mutableListOf()
        }
    }

    /**
     * Saves a list of users to the JSON file, overwriting existing data.
     */
    fun saveUsers(users: List<User>) {
        val file = getFile()
        try {
            val writer = FileWriter(file)
            Gson().toJson(users, writer)
            writer.close()
            // Log.d(TAG, "Saved ${users.size} users to file.") // Debug log removed
        } catch (e: IOException) {
            // Log.e(TAG, "Error saving user data: ${e.message}", e) // Debug log removed
            e.printStackTrace()
        }
    }

    /**
     * Adds a new user to the existing list and saves it.
     */
    fun addUser(newUser: User) {
        val currentUsers = loadUsers()
        currentUsers.add(newUser)
        saveUsers(currentUsers)
        // Log.d(TAG, "Added new user: ${newUser.username}. Total users: ${currentUsers.size}") // Debug log removed
    }

    /**
     * Updates an existing user in the list and saves it.
     * Finds the user by their ID.
     */
    fun updateUser(updatedUser: User) {
        val currentUsers = loadUsers()
        val index = currentUsers.indexOfFirst { it.id == updatedUser.id }
        if (index != -1) {
            currentUsers[index] = updatedUser
            saveUsers(currentUsers)
            // Log.d(TAG, "Updated user with ID ${updatedUser.id}") // Debug log removed
        } else {
            // Log.w(TAG, "User with ID ${updatedUser.id} not found for update.") // Debug log removed
        }
    }

    /**
     * Deletes a user by their ID and saves the updated list.
     */
    fun deleteUser(userId: String) {
        val currentUsers = loadUsers()
        val initialSize = currentUsers.size
        val updatedUsers = currentUsers.filter { it.id != userId }
        if (updatedUsers.size < initialSize) {
            saveUsers(updatedUsers)
            // Log.d(TAG, "Deleted user with ID $userId. Remaining users: ${updatedUsers.size}") // Debug log removed
        } else {
            // Log.w(TAG, "User with ID $userId not found for deletion.") // Debug log removed
        }
    }
}