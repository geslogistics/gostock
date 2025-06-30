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
        // Assuming your item_batch.xml has these IDs.
        // If they are different, please adjust them here.
        private val tvBatchId: TextView = itemView.findViewById(R.id.tv_batch_id)
        private val tvBatchTimestamp: TextView = itemView.findViewById(R.id.tv_batch_timestamp)
        private val tvItemCount: TextView = itemView.findViewById(R.id.tv_batch_item_count)
        private val tvBatchTimer: TextView = itemView.findViewById(R.id.tv_batch_timer)
        private val tvLocationsCounted: TextView = itemView.findViewById(R.id.tv_locations_counted)
        private val tvSkuCounted: TextView = itemView.findViewById(R.id.tv_sku_counted)
        private val tvQuantityCounted: TextView = itemView.findViewById(R.id.tv_quantity_counted)

        fun bind(batch: Batch) {
            tvBatchId.text = batch.batch_id
            tvItemCount.text = "Items: ${batch.item_count}"

            batch.transfer_date?.let {
                val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                tvBatchTimestamp.text = "On: ${sdf.format(Date(it))}"
            } ?: run {
                tvBatchTimestamp.text = "On: N/A"
            }

            // --- NEW DISPLAY LOGIC ---
            tvBatchTimer.text = "Duration: ${"%.2f".format(batch.batch_timer)} hrs"
            tvLocationsCounted.text = "Locations: ${batch.locations_counted}"
            tvSkuCounted.text = "SKUs: ${batch.sku_counted}"
            tvQuantityCounted.text = "Total Qty: ${batch.quantity_counted}"
            // --- END OF NEW LOGIC ---
        }
    }
}
