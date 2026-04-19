package com.example.myapplication.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One storage box, identified by a unique human-readable [name]. */
@Entity(
    tableName = "boxes",
    indices = [Index(value = ["name"], unique = true)]
)
data class BoxEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis()
)
