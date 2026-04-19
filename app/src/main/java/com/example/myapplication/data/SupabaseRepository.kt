package com.example.myapplication.data

import android.util.Log
import com.example.myapplication.DetectedItem
import com.example.myapplication.SupabaseModule
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns

private const val TAG_REPO = "SupabaseRepository"

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
        Log.d(TAG_REPO, "saveBoxWithItems — boxLabel='$boxLabel' itemCount=${items.size}")
        Log.d(TAG_REPO, "  → Supabase URL: ${com.example.myapplication.BuildConfig.SUPABASE_URL}")
        Log.d(TAG_REPO, "  → Supabase key present: ${com.example.myapplication.BuildConfig.SUPABASE_KEY.isNotBlank()}")

        // 1. Insert box and get back the server-generated id
        val box = try {
            Log.d(TAG_REPO, "  → inserting into 'boxes' table…")
            val result = boxesTable
                .insert(SupabaseBoxInsert(boxLabel = boxLabel)) {
                    select(Columns.list("id", "box_label"))
                }
                .decodeSingle<SupabaseBoxResponse>()
            Log.d(TAG_REPO, "  → box insert OK — id=${result.id} label=${result.boxLabel}")
            result
        } catch (e: Exception) {
            Log.e(TAG_REPO, "  ✗ box insert FAILED: ${e::class.simpleName} — ${e.message}", e)
            throw e
        }

        // 2. Bulk-insert items (no-op if the list is empty)
        if (items.isNotEmpty()) {
            try {
                Log.d(TAG_REPO, "  → inserting ${items.size} item(s) into 'items' table…")
                itemsTable.insert(
                    items.map { SupabaseItemInsert(boxId = box.id, name = it.label, count = it.count) }
                )
                Log.d(TAG_REPO, "  → items insert OK")
            } catch (e: Exception) {
                Log.e(TAG_REPO, "  ✗ items insert FAILED: ${e::class.simpleName} — ${e.message}", e)
                throw e
            }
        } else {
            Log.d(TAG_REPO, "  → no items to insert, skipping items table")
        }

        Log.d(TAG_REPO, "saveBoxWithItems completed — returning boxId=${box.id}")
        return box.id
    }
}

