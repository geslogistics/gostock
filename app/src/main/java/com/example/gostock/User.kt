package com.example.gostock // IMPORTANT: Replace with your actual package name

import android.os.Parcel
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

enum class UserRole {
    ADMIN, // Can manage users, export data, stocktake
    TEAMLEADER, // Can view/edit records, export data, stocktake
    SUPERVISOR, // Can view/edit records, export data, stocktake
    STOCKTAKER // Can only perform stocktaking
}

@Parcelize
data class User(
    override val id: String = UUID.randomUUID().toString(),
    val username: String,
    val passwordHash: String,
    val firstName: String = "",
    val lastName: String = "",
    val role: UserRole
) : Parcelable, Identifiable // Implement the interface
