package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.os.Parcel
import android.os.Parcelable
import java.util.UUID

// Enum to define user roles
enum class UserRole {
    ADMIN, // Can manage users, export data, stocktake
    SUPERVISOR, // Can view/edit records, export data, stocktake
    STOCKTAKER // Can only perform stocktaking
    // Add more roles as needed
}

// Data class to represent a user in the app
data class User(
    val id: String = UUID.randomUUID().toString(), // Unique ID for each user
    val username: String,
    val passwordHash: String, // Stored password hash, not plain text
    val firstName: String = "",
    val lastName: String = "",
    val role: UserRole
) : Parcelable { // Make it Parcelable to pass between activities

    constructor(parcel: Parcel) : this(
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        parcel.readString()!!,
        UserRole.valueOf(parcel.readString()!!) // Read Enum by name
    )

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeString(id)
        parcel.writeString(username)
        parcel.writeString(passwordHash)
        parcel.writeString(firstName)
        parcel.writeString(lastName)
        parcel.writeString(role.name) // Write Enum name
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<User> {
        override fun createFromParcel(parcel: Parcel): User {
            return User(parcel)
        }

        override fun newArray(size: Int): Array<User?> {
            return arrayOfNulls(size)
        }
    }
}