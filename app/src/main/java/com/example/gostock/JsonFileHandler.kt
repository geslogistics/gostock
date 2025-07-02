package com.example.gostock

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.io.IOException

class JsonFileHandler<T : Identifiable>(private val context: Context, private val filename: String, private val typeToken: TypeToken<MutableList<T>>) {

    private val TAG = "JsonFileHandler"

    private fun getFile(): File {
        return File(context.filesDir, filename)
    }

    fun loadRecords(): MutableList<T> {
        val file = getFile()
        if (!file.exists() || file.length() == 0L) {
            return mutableListOf()
        }

        return try {
            FileReader(file).use { reader ->
                Gson().fromJson<MutableList<T>>(reader, typeToken.type) ?: mutableListOf()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading or parsing records from $filename", e)
            mutableListOf()
        }
    }

    fun saveRecords(records: List<T>) {
        try {
            FileWriter(getFile()).use { writer ->
                Gson().toJson(records, writer)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Error saving data to $filename", e)
        }
    }

    fun addRecord(newRecord: T) {
        val currentRecords = loadRecords()
        currentRecords.add(newRecord)
        saveRecords(currentRecords)
    }

    fun addMultipleRecords(newRecords: List<T>) {
        if (newRecords.isEmpty()) return
        val currentRecords = loadRecords()
        currentRecords.addAll(newRecords)
        saveRecords(currentRecords)
    }

    fun updateRecord(updatedRecord: T) {
        val currentRecords = loadRecords()
        // The .id property is now resolved because of the 'T : Identifiable' constraint
        val index = currentRecords.indexOfFirst { it.id == updatedRecord.id }
        if (index != -1) {
            currentRecords[index] = updatedRecord
            saveRecords(currentRecords)
        } else {
            Log.w(TAG, "Record with ID ${updatedRecord.id} not found for update in $filename.")
        }
    }

    fun deleteRecord(recordId: String) {
        val currentRecords = loadRecords()
        val updatedRecords = currentRecords.filter { it.id != recordId }
        if (updatedRecords.size < currentRecords.size) {
            saveRecords(updatedRecords)
        } else {
            Log.w(TAG, "Record with ID $recordId not found for deletion in $filename.")
        }
    }

    fun clearData() {
        try {
            getFile().writeText("[]", Charsets.UTF_8)
        } catch (e: IOException) {
            Log.e(TAG, "Error clearing data from $filename", e)
        }
    }
}
