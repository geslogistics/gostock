package com.example.gostock // IMPORTANT: Replace with your actual package name

import java.security.MessageDigest

// Utility object for hashing passwords
object PasswordHasher {

    // Hashes a plain text password using SHA-256
    fun hashPassword(password: String): String {
        val bytes = password.toByteArray()
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(bytes)
        return digest.fold("", { str, it -> str + "%02x".format(it) }) // Convert to hex string
    }

    // Verifies a plain text password against a stored hash
    fun verifyPassword(plainTextPassword: String, storedHash: String): Boolean {
        return hashPassword(plainTextPassword) == storedHash
    }
}