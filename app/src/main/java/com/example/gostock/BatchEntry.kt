package com.example.gostock

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class BatchEntry(
    override var id: String,
    var timestamp: Long,
    var username: String,
    var locationBarcode: String,
    var skuBarcode: String,
    var quantity: Int,

    var batch_id: String,
    var batch_user: String,
    var transfer_date: Long,
    var receiver_user: String,

) : Parcelable, Identifiable
