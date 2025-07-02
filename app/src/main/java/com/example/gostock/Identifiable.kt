package com.example.gostock

/**
 * An interface for data classes that have a unique identifier.
 * This allows a generic class to know that any type implementing this
 * interface is guaranteed to have an 'id' property.
 */
interface Identifiable {
    val id: String
}
