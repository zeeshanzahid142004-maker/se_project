package com.example.myapplication.data

import com.example.myapplication.DetectedItem
import com.example.myapplication.SupabaseModule
import io.github.jan.supabase.postgrest.postgrest

/**
 * Writes boxes and their items to the remote Supabase database.
 *
 * Every method is a suspend function and must be called from a coroutine on
 * [kotlinx.coroutines.Dispatchers.IO]. Any network or server error is rethrown
 * so callers can handle it (e.g. show an error dialog and abort the local save).
 */
class SupabaseRepository {

    private val boxesTable = SupabaseModule.client.postgrest["boxes"]
    private val itemsTable = SupabaseModule.client.postgrest["items"]

    /**
     * Insert a new box row.  Throws if the network is unavailable or if the
     * Supabase server returns an error (e.g. duplicate name).
     */
    suspend fun createBox(name: String, createdAt: Long) {
        boxesTable.insert(SupabaseBox(name = name, createdAt = createdAt))
    }

    /**
     * Insert all [items] for [boxName].  A no-op when [items] is empty.
     * Throws on any failure.
     */
    suspend fun saveItems(boxName: String, items: List<DetectedItem>) {
        if (items.isEmpty()) return
        itemsTable.insert(
            items.map { SupabaseItem(boxName = boxName, label = it.label, count = it.count) }
        )
    }
}
