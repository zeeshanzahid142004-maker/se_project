package com.example.myapplication

import io.github.jan.supabase.postgrest.postgrest

class BoxRepImplementation : BoxRepository {

    private val boxesTables= SupabaseModule.client.postgrest["boxes"]
    override suspend fun getBox(BoxId: String): Result<Box_With_Item?> {
        return try {
            val boxDetails =
                boxesTables.select(columns = io.github.jan.supabase.postgrest.query.Columns.raw("*,items(*)"))
                {
                    filter {
                        eq("id", BoxId)
                           }
                }.decodeSingleOrNull<box_Data>()

            Result.success(Box_With_Item)
                }
            catch(e: Exception) {
                Result.failure(e)
            }


    }
        override suspend fun addBox(box: box_Data): Result<Unit> {
           return try {
               boxesTables.insert(box)
               Result.success(Unit)

           }catch (e: Exception){
               Result.failure(e)
           }
        }


    }