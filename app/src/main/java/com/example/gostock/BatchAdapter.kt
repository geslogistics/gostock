package com.example.gostock

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class BatchAdapter(
    private var batches: List<Batch>,
    private val onItemClicked: (Batch) -> Unit
) : RecyclerView.Adapter<BatchAdapter.BatchViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BatchViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_batch, parent, false)
        return BatchViewHolder(view)
    }

    override fun onBindViewHolder(holder: BatchViewHolder, position: Int) {
        val batch = batches[position]
        holder.bind(batch)
        holder.itemView.setOnClickListener { onItemClicked(batch) }
    }

    override fun getItemCount(): Int = batches.size

    fun updateData(newBatches: List<Batch>) {
        this.batches = newBatches
        notifyDataSetChanged()
    }

    class BatchViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        // Find all the TextViews from your item_batch.xml layout
        private val tvBatchId: TextView = itemView.findViewById(R.id.tv_batch_id)
        private val tvBatchUser: TextView = itemView.findViewById(R.id.tv_batch_user)
        private val tvBatchTimestamp: TextView = itemView.findViewById(R.id.tv_batch_timestamp)
        private val tvItemCount: TextView = itemView.findViewById(R.id.tv_batch_item_count)
        private val tvBatchTimer: TextView = itemView.findViewById(R.id.tv_batch_timer)
        private val tvLocationsCounted: TextView = itemView.findViewById(R.id.tv_locations_counted)
        private val tvSkuCounted: TextView = itemView.findViewById(R.id.tv_sku_counted)
        private val tvQuantityCounted: TextView = itemView.findViewById(R.id.tv_quantity_counted)

        fun bind(batch: Batch) {
            tvBatchId.text = batch.batch_id
            tvBatchUser.text = batch.batch_user ?: "N/A"
            tvItemCount.text = "${batch.item_count}"

            batch.transfer_date?.let {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                tvBatchTimestamp.text = sdf.format(Date(it))
            } ?: run {
                tvBatchTimestamp.text = "N/A"
            }

            // --- NEW DISPLAY LOGIC ---
            // Set the text for the new computed fields
            tvBatchTimer.text = String.format("%.2f hrs", batch.batch_timer)
            tvLocationsCounted.text = "${batch.locations_counted}"
            tvSkuCounted.text = "${batch.sku_counted}"
            tvQuantityCounted.text = "${batch.quantity_counted}"
            // --- END OF NEW LOGIC ---
        }
    }
}
