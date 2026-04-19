package com.example.myapplication

interface BoxRepository {
    suspend fun getBox(BoxId:String):Result<Box_With_Item?>
    suspend fun  addBox(box: box_Data): Result<Unit>
}