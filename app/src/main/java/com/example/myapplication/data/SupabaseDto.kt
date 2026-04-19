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
    @SerialName("box_label") val boxLabel: String
)

/**
 * Row returned after a box insert (PostgREST `Prefer: return=representation`).
 */
@Serializable
data class SupabaseBoxResponse(
    val id: Long,
    @SerialName("box_label") val boxLabel: String
)

/**
 * Payload sent when inserting a detected item.
 * Maps [DetectedItem.label] → `name`, [DetectedItem.count] → `count`.
 * `box_id` links the item to its parent box.
 * `discription` is an existing nullable boolean column — unused by the app.
 */
@Serializable
data class SupabaseItemInsert(
    @SerialName("box_id") val boxId: Long,
    val name: String,
    val count: Int
)

/**
 * Row returned when reading an item from Supabase.
 */
@Serializable
data class SupabaseItemResponse(
    val id: Long,
    val name: String,
    val count: Int
)

