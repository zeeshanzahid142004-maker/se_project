package com.example.myapplication.data

import com.example.myapplication.DetectedItem

/**
 * Single source of truth for boxes and their scanned items.
 *
 * All methods are suspend functions and must be called from a coroutine
 * (e.g. via [kotlinx.coroutines.Dispatchers.IO]).
 */
class BoxRepository(private val db: AppDatabase) {

    /** Create a new box with [name]; returns its auto-generated DB id. */
    suspend fun createBox(name: String): Long =
        db.boxDao().insert(BoxEntity(name = name))

    /** Look up a box by its display name (as encoded in the QR payload). */
    suspend fun getBoxByName(name: String): BoxEntity? =
        db.boxDao().getByName(name)

    /** Look up a box by its primary-key id. */
    suspend fun getBoxById(id: Long): BoxEntity? =
        db.boxDao().getById(id)

    /**
     * All box names currently stored — used by [BoxNameGenerator] to enforce
     * uniqueness before inserting a new box.
     */
    suspend fun getAllBoxNames(): List<String> =
        db.boxDao().getAllNames()

    /**
     * Persist [items] for the given [boxId], replacing any previously saved
     * items for that box to keep the data idempotent.
     */
    suspend fun saveItems(boxId: Long, items: List<DetectedItem>) {
        db.itemDao().deleteForBox(boxId)
        db.itemDao().insertAll(
            items.map { ItemEntity(boxId = boxId, label = it.label, count = it.count) }
        )
    }

    /** Load all items for [boxId] as UI-friendly [DetectedItem] objects. */
    suspend fun getItems(boxId: Long): List<DetectedItem> =
        db.itemDao().getItemsForBox(boxId)
            .map { DetectedItem(it.label, it.count) }

    /** Total number of boxes. */
    suspend fun boxCount(): Int = db.boxDao().count()

    /** Total number of item units across all boxes. */
    suspend fun totalItemCount(): Int = db.itemDao().totalCount()
}
