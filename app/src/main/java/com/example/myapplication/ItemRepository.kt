package com.example.myapplication

interface ItemRepository
{
    suspend fun addItem(item: Item):Result<Unit>
    suspend fun getItem(itemId:String):Result<Item?>


}