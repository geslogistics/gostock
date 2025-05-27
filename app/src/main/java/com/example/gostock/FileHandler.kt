package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.content.Context
import android.util.Log // Import Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

class FileHandler(private val context: Context) {

    private val FILENAME = "stock_data.json"
    private val TAG = "FileHandler" // Tag for logging

    private fun getFile(): File {
        val file = File(context.filesDir, FILENAME)
        Log.d(TAG, "File path: ${file.absolutePath}") // Log file path
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
            reader.close() // Close the reader
            Log.d(TAG, "Loaded entries: ${loadedList?.size ?: 0} entries.")
            loadedList ?: mutableListOf()
        } catch (e: IOException) {
            Log.e(TAG, "IOException loading entries: ${e.message}")
            e.printStackTrace()
            mutableListOf()
        } catch (e: Exception) {
            Log.e(TAG, "General Exception loading entries: ${e.message}")
            e.printStackTrace()
            mutableListOf()
        }
    }

    // ... (rest of FileHandler class: saveStockEntries, addStockEntry, updateStockEntry, deleteStockEntry) ...

    // Make sure to include the rest of your FileHandler code as is.
    // For example:
    fun saveStockEntries(entries: List<StockEntry>) {
        val file = getFile()
        try {
            val writer = FileWriter(file)
            Gson().toJson(entries, writer)
            writer.close()
            Log.d(TAG, "Saved ${entries.size} entries to file.")
        } catch (e: IOException) {
            Log.e(TAG, "Error saving data: ${e.message}", e)
        }
    }

    fun addStockEntry(newEntry: StockEntry) {
        val currentEntries = loadStockEntries()
        currentEntries.add(newEntry)
        saveStockEntries(currentEntries)
        Log.d(TAG, "Added new entry: $newEntry. Total entries: ${currentEntries.size}")
    }

    fun updateStockEntry(updatedEntry: StockEntry) {
        val currentEntries = loadStockEntries()
        val index = currentEntries.indexOfFirst { it.id == updatedEntry.id }
        if (index != -1) {
            currentEntries[index] = updatedEntry
            saveStockEntries(currentEntries)
            Log.d(TAG, "Updated entry with ID ${updatedEntry.id}")
        } else {
            Log.w(TAG, "Entry with ID ${updatedEntry.id} not found for update.")
        }
    }

    fun deleteStockEntry(entryId: String) {
        val currentEntries = loadStockEntries()
        val initialSize = currentEntries.size
        val updatedEntries = currentEntries.filter { it.id != entryId }
        if (updatedEntries.size < initialSize) {
            saveStockEntries(updatedEntries)
            Log.d(TAG, "Deleted entry with ID $entryId. Remaining entries: ${updatedEntries.size}")
        } else {
            Log.w(TAG, "Entry with ID $entryId not found for deletion.")
        }
    }
}