package com.example.gostock

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

// The @Parcelize annotation automatically handles all the boilerplate code
// for making a class Parcelable, which is the modern Android standard.
@Parcelize
data class StockEntryArchived(
    override var id: String,
    var timestamp: Long,
    var username: String,
    var locationBarcode: String,
    var skuBarcode: String,
    var quantity: Int,

    var action_user: String,
    var action_timestamp: Long,
    var action: String

) : Parcelable, Identifiable
