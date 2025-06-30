package com.example.gostock

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

// The @Parcelize annotation automatically handles all the boilerplate code
// for making a class Parcelable, which is the modern Android standard.
@Parcelize
data class StockEntry(
    val id: String = UUID.randomUUID().toString(),
    // Changed to Long for correct sorting and calculations.
    var timestamp: String,
    var username: String,
    // Renamed for consistency with the rest of the app's logic.
    var locationBarcode: String,
    var skuBarcode: String,
    var quantity: Int,

    // --- NEW NULLABLE FIELDS ---
    // These are added for the transfer process. They are nullable
    // so they don't break existing records or activities.
    var batchID: String? = null,
    var sender_user: String? = null,
    var transfer_date: Long? = null,
    var receiver_user: String? = null
) : Parcelable
