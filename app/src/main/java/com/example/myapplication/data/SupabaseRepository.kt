package com.example.myapplication.data

import android.util.Log
import com.example.myapplication.DetectedItem
import com.example.myapplication.SupabaseModule
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.serialization.Serializable

@Serializable
private data class SystemAlertInsert(
    val title    : String,
    val message  : String,
    val severity : String,
    val is_read  : Boolean = false,
    val created_at: String
)

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
    private val usersTable = SupabaseModule.client.postgrest["warehouse_users"]

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
    suspend fun saveBoxWithItems(boxLabel: String, items: List<DetectedItem>, createdBy: String): Long {
        Log.d(TAG_REPO, "saveBoxWithItems — boxLabel='$boxLabel' itemCount=${items.size} createdBy=$createdBy")
        Log.d(TAG_REPO, "  → Supabase URL: ${com.example.myapplication.BuildConfig.SUPABASE_URL}")
        Log.d(TAG_REPO, "  → Supabase key present: ${com.example.myapplication.BuildConfig.SUPABASE_KEY.isNotBlank()}")

        // 1. Insert box and get back the server-generated id
        val box = try {
            Log.d(TAG_REPO, "  → inserting into 'boxes' table…")
            val result = boxesTable
                .insert(SupabaseBoxInsert(boxLabel = boxLabel, createdBy = createdBy)) {
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

    /**
     * Fetches a box and its items from Supabase by [boxLabel].
     *
     * @return A pair of the box response and its item list, or `null` if no
     *         box with that label exists in the remote database.
     * @throws Exception on any network or server error — callers should show
     *         an internet-connectivity error to the user.
     */
    suspend fun fetchBoxWithItems(boxLabel: String): Pair<SupabaseBoxResponse, List<com.example.myapplication.DetectedItem>>? {
        Log.d(TAG_REPO, "fetchBoxWithItems — boxLabel='$boxLabel'")

        val boxes = try {
            Log.d(TAG_REPO, "  → querying 'boxes' for label='$boxLabel'…")
            boxesTable
                .select(Columns.list("id", "box_label", "created_at")) {
                    filter { eq("box_label", boxLabel) }
                }
                .decodeList<SupabaseBoxResponse>()
        } catch (e: Exception) {
            Log.e(TAG_REPO, "  ✗ box fetch FAILED: ${e::class.simpleName} — ${e.message}", e)
            throw e
        }

        if (boxes.isEmpty()) {
            Log.d(TAG_REPO, "  → no box found with label='$boxLabel'")
            return null
        }

        val box = boxes.first()
        Log.d(TAG_REPO, "  → found box id=${box.id} label=${box.boxLabel}")

        val items = try {
            Log.d(TAG_REPO, "  → querying 'items' for box_id=${box.id}…")
            itemsTable
                .select(Columns.list("id", "name", "count")) {
                    filter { eq("box_id", box.id) }
                }
                .decodeList<SupabaseItemResponse>()
                .map { com.example.myapplication.DetectedItem(it.name, it.count) }
        } catch (e: Exception) {
            Log.e(TAG_REPO, "  ✗ items fetch FAILED: ${e::class.simpleName} — ${e.message}", e)
            throw e
        }

        Log.d(TAG_REPO, "fetchBoxWithItems completed — boxId=${box.id}, ${items.size} item(s)")
        return Pair(box, items)
    }

    suspend fun fetchEmployeeProfile(userId: String): WarehouseUser? {
        Log.d(TAG_REPO, "fetchEmployeeProfile — userId=$userId")
        return try {
            usersTable
                .select(Columns.list("id", "full_name", "role", "email")) {
                    filter { eq("id", userId) }
                }
                .decodeSingleOrNull<WarehouseUser>()
        } catch (e: Exception) {
            Log.e(TAG_REPO, "  ✗ employee fetch FAILED: ${e::class.simpleName} — ${e.message}", e)
            throw e
        }
    }

    suspend fun fetchActivityDates(userId: String, year: Int, month: Int): List<LocalDate> {
        Log.d(TAG_REPO, "fetchActivityDates — userId=$userId year=$year month=$month")
        val start = LocalDate.of(year, month, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
        val end = LocalDate.of(year, month, 1).plusMonths(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
        val rows = boxesTable
            .select(Columns.list("id", "created_at")) {
                filter {
                    eq("created_by", userId)
                    gte("created_at", start)
                    lt("created_at", end)
                }
            }
            .decodeList<SupabaseBoxActivityRow>()
        return rows.map {
            OffsetDateTime.parse(it.createdAt)
                .atZoneSameInstant(ZoneId.systemDefault())
                .toLocalDate()
        }.distinct()
    }

    suspend fun fetchDayStats(userId: String, date: LocalDate): DayStats {
        Log.d(TAG_REPO, "fetchDayStats — userId=$userId date=$date")
        val start = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
        val end = date.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toString()
        val boxes = boxesTable
            .select(Columns.list("id", "created_at")) {
                filter {
                    eq("created_by", userId)
                    gte("created_at", start)
                    lt("created_at", end)
                }
            }
            .decodeList<SupabaseBoxActivityRow>()
        val boxIds = boxes.map { it.id }
        val totalItems = fetchItemsCountForBoxes(boxIds)
        return DayStats(boxes = boxIds.size, items = totalItems)
    }

    suspend fun fetchTotalStats(userId: String): TotalStats {
        Log.d(TAG_REPO, "fetchTotalStats — userId=$userId")
        val boxes = boxesTable
            .select(Columns.list("id")) {
                filter { eq("created_by", userId) }
            }
            .decodeList<SupabaseBoxActivityRow>()
        val boxIds = boxes.map { it.id }
        val totalItems = fetchItemsCountForBoxes(boxIds)
        return TotalStats(totalBoxes = boxIds.size, totalItems = totalItems)
    }

    suspend fun submitManagerReport(
        userId: String,
        reason: String,
        notes: String,
    ) {
        // 1. Fetch the user's profile to get their real name
        val profile = fetchEmployeeProfile(userId)

        // Fallback to the ID just in case the profile fetch fails or the name is blank
        val reporterName = if (profile != null && profile.fullName.isNotBlank()) {
            profile.fullName
        } else {
            "Unknown User ($userId)"
        }

        // 2. Build the message with the real name
        val title   = "Scanner Issue: $reason"
        val message = buildString {
            append("Reported by: $reporterName\n")
            append("Reason: $reason\n")
            if (notes.isNotBlank()) append("Notes: $notes")
        }

        // 3. Insert the alert with the exact current UTC timestamp
        val alert = SystemAlertInsert(
            title      = title,
            message    = message,
            severity   = "warning",
            created_at = java.time.Instant.now().toString()
        )

        SupabaseModule.client.postgrest["system_alerts"].insert(alert)
    }
    private suspend fun fetchItemsCountForBoxes(boxIds: List<Long>): Int {
        if (boxIds.isEmpty()) return 0
        val items = itemsTable
            .select(Columns.list("count")) {
                filter { isIn("box_id", boxIds) }
            }
            .decodeList<SupabaseItemCountRow>()
        return items.sumOf { it.count }
    }
}

data class DayStats(
    val boxes: Int,
    val items: Int
)

data class TotalStats(
    val totalBoxes: Int,
    val totalItems: Int
)
