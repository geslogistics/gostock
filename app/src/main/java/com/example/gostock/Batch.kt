package com.example.gostock

/**
 * Represents a single batch of transferred stock entries.
 * This class is used to group records for display in the BatchListActivity.
 */
data class Batch(
    val batch_id: String,
    val batch_user: String?,
    val first_entry_date: Long?,
    val last_entry_date: Long?,
    val transfer_date: Long?,
    val receiver_user: String?,
    val item_count: Int,
    val batch_timer: Float,
    val locations_counted: Int,
    val sku_counted: Int,
    val quantity_counted: Int,
    val entries: List<StockEntry>
)
