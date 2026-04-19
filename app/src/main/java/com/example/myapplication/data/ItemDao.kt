package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ItemDao {
    /** Insert (or replace) a batch of items for a box. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ItemEntity>)

    @Query("SELECT * FROM items WHERE boxId = :boxId")
    suspend fun getItemsForBox(boxId: Long): List<ItemEntity>

    /** Total number of item units across all boxes. */
    @Query("SELECT COALESCE(SUM(count), 0) FROM items")
    suspend fun totalCount(): Int

    @Query("DELETE FROM items WHERE boxId = :boxId")
    suspend fun deleteForBox(boxId: Long)
}
