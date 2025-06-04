package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Adapter for displaying recent StockEntry items in a RecyclerView
class RecentEntryAdapter(
    private var entries: List<StockEntry> // List of recent entries
) : RecyclerView.Adapter<RecentEntryAdapter.RecentEntryViewHolder>() {

    class RecentEntryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvRecentLocation: TextView = itemView.findViewById(R.id.tv_recent_location)
        val tvRecentSku: TextView = itemView.findViewById(R.id.tv_recent_sku)
        val tvRecentQuantity: TextView = itemView.findViewById(R.id.tv_recent_quantity)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecentEntryViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_recent_entry, parent, false)
        return RecentEntryViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecentEntryViewHolder, position: Int) {
        val entry = entries[position]
        holder.tvRecentLocation.text = "${entry.locationBarcode}"
        holder.tvRecentSku.text = "${entry.skuBarcode}"
        holder.tvRecentQuantity.text = "${entry.quantity}"
        // You could add an OnClickListener here if you wanted to allow tapping recent entries to edit them
    }

    override fun getItemCount(): Int = entries.size

    // Method to update the data in the adapter and refresh the RecyclerView
    fun updateData(newEntries: List<StockEntry>) {
        this.entries = newEntries // Replace the entire list
        notifyDataSetChanged() // Notifies the RecyclerView that the data set has changed
    }
}