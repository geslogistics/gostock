package com.example.gostock

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Represents a single batch of transferred stock entries.
 * This class is now Parcelable to be passed between activities.
 */
@Parcelize
data class BatchEntryArchived(
    override var id: String,
    var timestamp: String,
    var username: String,
    var locationBarcode: String,
    var skuBarcode: String,
    var quantity: Int,

    var batch_id: String,
    var batch_user: String,
    var transfer_date: Long,
    var receiver_user: String,

    var action_user: String,
    var action_timestamp: String,
    var action: String
) : Parcelable, Identifiable
