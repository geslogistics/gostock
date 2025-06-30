package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

// MODIFIED: FILENAME is now a constructor parameter
class FileHandler(private val context: Context, private val filename: String) {

    private val TAG = "FileHandler"

    private fun getFile(): File {
        val file = File(context.filesDir, filename) // Use the filename parameter
        Log.d(TAG, "File path: ${file.absolutePath}")
        return file
    }

    fun loadStockEntries(): MutableList<StockEntry> {
        val file = getFile()
        Log.d(TAG, "Attempting to load from: ${file.absolutePath}")
        Log.d(TAG, "File exists: ${file.exists()}, File length: ${file.length()}")

        if (!file.exists() || file.length() == 0L) {
            Log.d(TAG, "File does not exist or is empty. Returning empty list.")
            return mutableListOf()
        }

        return try {
            val reader = FileReader(file)
            val listType = object : TypeToken<MutableList<StockEntry>>() {}.type
            val loadedList = Gson().fromJson<MutableList<StockEntry>>(reader, listType)
            reader.close()
            Log.d(TAG, "Loaded entries: ${loadedList?.size ?: 0} entries from $filename.") // Log filename
            loadedList ?: mutableListOf()
        } catch (e: IOException) {
            Log.e(TAG, "IOException loading entries from $filename: ${e.message}") // Log filename
            e.printStackTrace()
            mutableListOf()
        } catch (e: Exception) {
            Log.e(TAG, "General Exception loading entries from $filename: ${e.message}") // Log filename
            e.printStackTrace()
            mutableListOf()
        }
    }

    fun saveStockEntries(entries: List<StockEntry>) {
        val file = getFile()
        try {
            val writer = FileWriter(file)
            Gson().toJson(entries, writer)
            writer.close()
            Log.d(TAG, "Saved ${entries.size} entries to $filename.") // Log filename
        } catch (e: IOException) {
            Log.e(TAG, "Error saving data to $filename: ${e.message}", e) // Log filename
        }
    }

    fun addStockEntry(newEntry: StockEntry) {
        val currentEntries = loadStockEntries()
        currentEntries.add(newEntry)
        saveStockEntries(currentEntries)
        Log.d(TAG, "Added new entry to $filename: $newEntry. Total entries: ${currentEntries.size}") // Log filename
    }

    fun updateStockEntry(updatedEntry: StockEntry) {
        val currentEntries = loadStockEntries()
        val index = currentEntries.indexOfFirst { it.id == updatedEntry.id }
        if (index != -1) {
            currentEntries[index] = updatedEntry
            saveStockEntries(currentEntries)
            Log.d(TAG, "Updated entry with ID ${updatedEntry.id} in $filename.") // Log filename
        } else {
            Log.w(TAG, "Entry with ID ${updatedEntry.id} not found for update in $filename.") // Log filename
        }
    }

    fun deleteStockEntry(entryId: String) {
        val currentEntries = loadStockEntries()
        val initialSize = currentEntries.size
        val updatedEntries = currentEntries.filter { it.id != entryId }
        if (updatedEntries.size < initialSize) {
            saveStockEntries(updatedEntries)
            Log.d(TAG, "Deleted entry with ID $entryId from $filename. Remaining entries: ${updatedEntries.size}") // Log filename
        } else {
            Log.w(TAG, "Entry with ID $entryId not found for deletion in $filename.") // Log filename
        }
    }

    /** Clears all stock entries by deleting the data file. */
    fun clearStockEntries(): Boolean {
        val file = getFile()
        if (file.exists()) {
            val deleted = file.delete()
            Log.d(TAG, "Stock data file $filename deleted: $deleted") // Log filename
            return deleted
        }
        Log.d(TAG, "Stock data file $filename not found, no need to delete.") // Log filename
        return true // Consider it cleared if file doesn't exist
    }

    /** Adds a list of new stock entries to the existing list and saves it. More efficient for bulk additions. */
    fun addMultipleStockEntries(newEntries: List<StockEntry>) {
        if (newEntries.isEmpty()) return
        val currentEntries = loadStockEntries()
        currentEntries.addAll(newEntries)
        saveStockEntries(currentEntries)
        Log.d(TAG, "Added ${newEntries.size} new entries to $filename. Total entries: ${currentEntries.size}") // Log filename
    }

    // --- Add this function to your FileHandler.kt file ---

    /**
     * Reads the entire content of the JSON file into a single string.
     * @return The file content as a String, or null if the file doesn't exist or an error occurs.
     */
    fun readJsonFromFile(): String? {
        return try {
            val file = File(context.filesDir, filename)
            if (!file.exists()) {
                Log.w("FileHandler", "File not found: $filename")
                null
            } else {
                file.readText(Charsets.UTF_8)
            }
        } catch (e: IOException) {
            Log.e("FileHandler", "Error reading from file $filename", e)
            null
        }
    }

    fun clearData() {
        try {
            val file = File(context.filesDir, filename)
            // Overwrite the file with an empty JSON array "[]"
            file.writeText("[]", Charsets.UTF_8)
            Log.d("FileHandler", "Successfully cleared data from $filename.")
        } catch (e: IOException) {
            Log.e("FileHandler", "Error clearing data from $filename", e)
        }
    }
}