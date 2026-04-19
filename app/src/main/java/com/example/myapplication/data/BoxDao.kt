package com.example.myapplication.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BoxDao {
    /** Insert a new box; returns the auto-generated row id. */
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(box: BoxEntity): Long

    @Query("SELECT * FROM boxes WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): BoxEntity?

    @Query("SELECT * FROM boxes WHERE name = :name LIMIT 1")
    suspend fun getByName(name: String): BoxEntity?

    /** All box names — used for uniqueness checking during name generation. */
    @Query("SELECT name FROM boxes")
    suspend fun getAllNames(): List<String>

    /** Total number of boxes in the database. */
    @Query("SELECT COUNT(*) FROM boxes")
    suspend fun count(): Int

    @Query("SELECT * FROM boxes ORDER BY createdAt DESC")
    suspend fun getAll(): List<BoxEntity>
}
