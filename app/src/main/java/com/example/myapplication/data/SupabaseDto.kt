package com.example.myapplication.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Actual Supabase schema ────────────────────────────────────────────────────
//
//  CREATE TABLE public.boxes (
//    id         bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
//    created_at timestamptz NOT NULL DEFAULT now(),
//    box_label  text        NOT NULL UNIQUE,
//    item_id    bigint      REFERENCES public.items(id)   -- nullable after migration
//  );
//
//  CREATE TABLE public.items (
//    id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
//    created_at  timestamptz NOT NULL DEFAULT now(),
//    name        text        NOT NULL DEFAULT '',
//    discription boolean,
//    -- added via migration:
//    count       integer     NOT NULL DEFAULT 1,
//    box_id      bigint      REFERENCES public.boxes(id) ON DELETE CASCADE
//  );
//
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Payload sent when inserting a new box.
 * `id` and `created_at` are omitted — the server generates them automatically.
 * `item_id` is omitted — items reference boxes via `items.box_id`.
 */
@Serializable
data class SupabaseBoxInsert(
    @SerialName("box_label") val boxLabel: String,
    @SerialName("created_by") val createdBy: String
)

/**
 * Row returned after a box insert (PostgREST `Prefer: return=representation`).
 * [createdAt] is an ISO-8601 timestamp string as returned by Supabase/PostgREST,
 * e.g. "2024-01-01T12:00:00+00:00".
 */
@Serializable
data class SupabaseBoxResponse(
    val id: Long,
    @SerialName("box_label") val boxLabel: String,
    @SerialName("created_at") val createdAt: String = ""
)

/**
 * Payload sent when inserting a detected item.
 * Maps [DetectedItem.label] → `name`, [DetectedItem.count] → `count`.
 * `box_id` links the item to its parent box; pass `null` when the item is not
 * yet assigned to a box (e.g. when filing a complaint).
 */
@Serializable
data class SupabaseItemInsert(
    @SerialName("box_id") val boxId: Long?,
    val name: String,
    val count: Int
)

/**
 * Payload sent when inserting a new complaint.
 * The server generates `id`, `status` (default 'pending'), and `created_at`.
 */
@Serializable
data class ComplaintInsert(
    @SerialName("user_id")        val userId: String,
    @SerialName("item_id")        val itemId: Long,
    @SerialName("complaint_info") val complaintInfo: String
)

/**
 * Row returned when reading an item from Supabase.
 * Fields other than [id] are given defaults so that a partial response
 * (e.g. when only `id` is selected) does not cause a MissingFieldException.
 */
@Serializable
data class SupabaseItemResponse(
    val id: Long,
    val name: String? = null,
    val count: Int? = null
)

/**
 * Lightweight DTO used when only the auto-generated `id` is needed after an
 * insert (e.g. inside [SupabaseRepository.submitItemComplaint]).
 * Keeping this separate avoids requiring all other columns to be returned.
 */
@Serializable
data class InsertedItemId(val id: Long)

@Serializable
data class WarehouseUser(
    val id: String,
    @SerialName("full_name") val fullName: String = "",
    val role: String = "",
    val email: String = ""
)

@Serializable
data class SupabaseBoxActivityRow(
    val id: Long,
    @SerialName("created_at") val createdAt: String = ""
)

@Serializable
data class SupabaseItemCountRow(
    @SerialName("count") val count: Int
)
