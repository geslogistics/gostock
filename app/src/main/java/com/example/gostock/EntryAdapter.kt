package com.example.gostock // IMPORTANT: Replace with your actual package name

// import android.util.Log // Log import removed for cleaner output
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class EntryAdapter(
    initialEntries: List<StockEntry>, // Take initial entries as a regular parameter
    private val onItemClick: (StockEntry) -> Unit
) : RecyclerView.Adapter<EntryAdapter.EntryViewHolder>() {

    // private val TAG = "EntryAdapter" // Tag for logging, removed for cleaner output

    // CHANGE: Declare 'entries' as a mutable 'var' property inside the class.
    private var entries: MutableList<StockEntry> = initialEntries.toMutableList() // Initialize it here

    // ViewHolder class to hold references to the views in each list item
    class EntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timestamp: TextView = itemView.findViewById(R.id.tv_item_timestamp)
        val username: TextView = itemView.findViewById(R.id.tv_item_username)
        val location: TextView = itemView.findViewById(R.id.tv_item_location)
        val sku: TextView = itemView.findViewById(R.id.tv_item_sku)
        val quantity: TextView = itemView.findViewById(R.id.tv_item_quantity)
        val itemLayout: LinearLayout = itemView.findViewById(R.id.record_item_layout)
    }

    // Called when RecyclerView needs a new ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EntryViewHolder {
        // Log.d(TAG, "onCreateViewHolder: Creating new ViewHolder for item type $viewType") // Debug log removed
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_stock_entry, parent, false)
        return EntryViewHolder(view)
    }

    // Called to bind data to a ViewHolder
    override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
        if (position < 0 || position >= entries.size) { // Defensive check
            // Log.e(TAG, "onBindViewHolder: Invalid position $position. Entries size: ${entries.size}") // Debug log removed
            return
        }
        val entry = entries[position]
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val formattedTimestamp = entry.timestamp?.let { sdf.format(Date(it)) } ?: ""

        holder.timestamp.text = formattedTimestamp
        holder.username.text = "${entry.username}"
        holder.location.text = "${entry.locationBarcode}"
        holder.sku.text = "${entry.skuBarcode}"
        holder.quantity.text = "${entry.quantity}"

        // Set up click listener for the entire item
        holder.itemLayout.setOnClickListener {
            // Log.d(TAG, "Item click detected for ID: ${entry.id}") // Debug log removed
            onItemClick(entry) // Invoke the lambda passed from the Activity
        }
    }

    // Returns the total number of items in the list
    override fun getItemCount(): Int {
        val count = entries.size
        // Log.d(TAG, "getItemCount: Returning total item count: $count") // Debug log removed
        return count
    }

    // Method to update the data in the adapter and refresh the RecyclerView
    fun updateData(newEntries: List<StockEntry>) {
        // Log.d(TAG, "updateData: Received ${newEntries.size} new entries.") // Debug log removed
        entries = newEntries.toMutableList() // Replace the entire list
        // Log.d(TAG, "updateData: Entries size after replacement: ${entries.size}") // Debug log removed
        notifyDataSetChanged()
        // Log.d(TAG, "updateData: Notified data set changed. Final entries size: ${entries.size}") // Debug log removed
    }
}