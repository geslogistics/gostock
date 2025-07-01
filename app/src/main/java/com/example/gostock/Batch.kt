package com.example.gostock

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a single batch of transferred stock entries.
 * This class is now Parcelable to be passed between activities.
 */
@Parcelize
data class Batch(
    val batch_id: String,
    val batch_user: String?,
    val transfer_date: Long?,
    val receiver_user: String?,
    val item_count: Int,
    val batch_timer: Float,
    val locations_counted: Int,
    val sku_counted: Int,
    val quantity_counted: Int,
    val entries: List<StockEntry>, // The actual records in this batch
    val first_entry_date: Long?,
    val last_entry_date: Long?
) : Parcelable
