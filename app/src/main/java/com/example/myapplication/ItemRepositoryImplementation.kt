package com.example.myapplication

import io.github.jan.supabase.postgrest.postgrest

class ItemRepositoryImplementation: ItemRepository {
    private val itemsTable= SupabaseModule.client.postgrest["items"]
    override suspend fun addItem(item: Item): Result<Unit> {
        return try {
           itemsTable.insert(item)
            Result.success(Unit)

        }catch (e: Exception){
            Result.failure(e)
        }
    }

    override suspend fun getItem(itemId: String): Result<Item?> {
        return try {
            val itemDetails=itemsTable.select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("*"))
            {
                filter { eq("item_id",itemId) }
            }.decodeSingleOrNull<Item>()

            Result.success(itemDetails)
        }catch (e: Exception){
            Result.failure(e)
        }
    }
}