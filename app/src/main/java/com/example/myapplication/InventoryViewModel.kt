package com.example.myapplication

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BoxViewModel(
    private val repository: BoxRepository= BoxRepImplementation()
): ViewModel(){
    private val _currentBox= MutableStateFlow<Box_With_Item?>(null)
    val currentBox: StateFlow<Box_With_Item?> =_currentBox.asStateFlow()


    private val _errorMessage= MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()


    fun scanBox(qrCodeLabel:String){
        viewModelScope.launch{
            repository.getBox(qrCodeLabel).fold(
                onSuccess = {
                        box_Data->
                    _currentBox.value= box_Data
                    _errorMessage.value=null
                    println("Scanned successfully: ${box_Data?.item?.item_id}")
                },
                onFailure = {
                    error->
                    _currentBox.value=null
                    _errorMessage.value=error.message ?:"Unknown error"
                    println("Scan failed: ${error.message}")
                }
            )


        }
    }


    fun clearError() {
        _errorMessage.value = null
    }
}