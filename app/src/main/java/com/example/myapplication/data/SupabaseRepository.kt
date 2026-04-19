package com.example.myapplication.data

import com.example.myapplication.DetectedItem
import com.example.myapplication.SupabaseModule
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

/**
 * Writes boxes and their items to the remote Supabase database.
 *
 * Schema (after migration):
 *   boxes : id (auto), created_at (auto), box_label (unique), item_id (nullable)
 *   items : id (auto), created_at (auto), name, discription, count, box_id (FK → boxes.id)
 *
 * Every method is a suspend function and must be called from a coroutine on
 * [kotlinx.coroutines.Dispatchers.IO]. Any network or server error is rethrown so
 * callers can show an error and abort the local Room save.
 */
class SupabaseRepository {

    private val boxesTable = SupabaseModule.client.postgrest["boxes"]
    private val itemsTable = SupabaseModule.client.postgrest["items"]

    /**
     * Atomically saves a box and all its detected items to Supabase.
     *
     * Flow:
     *   1. Insert a row into `boxes` with [boxLabel]; the server returns the
     *      auto-generated `id`.
     *   2. Bulk-insert all [items] into `items`, each referencing the new box id.
     *
     * Throws on any network or server error — no local fallback is performed.
     *
     * @return The server-generated box id (can be used to link local Room records).
     */
    suspend fun saveBoxWithItems(boxLabel: String, items: List<DetectedItem>): Long {
        // 1. Insert box and get back the server-generated id
        val box = boxesTable
            .insert(SupabaseBoxInsert(boxLabel = boxLabel)) {
                select(Columns.list("id", "box_label"))
            }
            .decodeSingle<SupabaseBoxResponse>()

        // 2. Bulk-insert items (no-op if the list is empty)
        if (items.isNotEmpty()) {
            itemsTable.insert(
                items.map { SupabaseItemInsert(boxId = box.id, name = it.label, count = it.count) }
            )
        }

        return box.id
    }
}

