package com.example.gostock

import android.os.Parcel
import android.os.Parcelable
import java.util.UUID

// Data class to represent a single stock entry
// Implement Parcelable to pass between activities
data class StockEntry(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: String,
    val username: String,
    val locationBarcode: String,
    val skuBarcode: String,
    val quantity: Int
) : Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readInt()
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(timestamp)
        parcel.writeString(username)
        parcel.writeString(locationBarcode)
        parcel.writeString(skuBarcode)
        parcel.writeInt(quantity)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<StockEntry> {
        override fun createFromParcel(parcel: Parcel): StockEntry {
            return StockEntry(parcel)
        }

        override fun newArray(size: Int): Array<StockEntry?> {
            return arrayOfNulls(size)
        }
    }
}