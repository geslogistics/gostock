package com.example.gostock

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

// The @Parcelize annotation automatically handles all the boilerplate code
// for making a class Parcelable, which is the modern Android standard.
@Parcelize
data class StockEntry(
    override val id: String = UUID.randomUUID().toString(),
    var timestamp: Long,
    var username: String,
    var locationBarcode: String,
    var skuBarcode: String,
    var quantity: Int,

) : Parcelable, Identifiable
