package com.example.myapplication.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Row in the Supabase `boxes` table.
 *
 * Expected DDL:
 *   CREATE TABLE boxes (
 *       name       TEXT PRIMARY KEY,
 *       created_at BIGINT NOT NULL
 *   );
 */
@Serializable
data class SupabaseBox(
    val name: String,
    @SerialName("created_at") val createdAt: Long
)

/**
 * Row in the Supabase `items` table.
 *
 * Expected DDL:
 *   CREATE TABLE items (
 *       id       BIGSERIAL PRIMARY KEY,
 *       box_name TEXT    NOT NULL REFERENCES boxes(name) ON DELETE CASCADE,
 *       label    TEXT    NOT NULL,
 *       count    INTEGER NOT NULL
 *   );
 */
@Serializable
data class SupabaseItem(
    @SerialName("box_name") val boxName: String,
    val label: String,
    val count: Int
)
