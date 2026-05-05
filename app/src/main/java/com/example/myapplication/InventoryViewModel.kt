package com.example.myapplication

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myapplication.data.DayStats
import com.example.myapplication.data.SupabaseRepository
import com.example.myapplication.data.TotalStats
import com.example.myapplication.data.WarehouseUser
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

private const val TAG_INVENTORY_VM = "InventoryViewModel"

data class InventoryUiState(
    val profile: WarehouseUser? = null,
    val profileMessage: String? = null,
    val activityDates: List<LocalDate> = emptyList(),
    val totalStats: TotalStats? = null,
    val profileLoading: Boolean = true,
    val activityLoading: Boolean = true,
    val statsLoading: Boolean = true,
    val selectedDay: LocalDate? = null,
    val dayStats: DayStats? = null,
    val dayStatsLoading: Boolean = false
)

class InventoryViewModel(
    private val repository: SupabaseRepository = SupabaseRepository()
) : ViewModel() {

    val currentMonth: YearMonth = YearMonth.now()

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    private var userId: String? = null

    init {
        val id = SupabaseModule.client.auth.currentUserOrNull()?.id
        userId = id
        if (id == null) {
            _uiState.value = InventoryUiState(
                profileLoading = false,
                activityLoading = false,
                statsLoading = false,
                profileMessage = "No employee signed in",
                totalStats = TotalStats(0, 0)
            )
        } else {
            loadData(id)
        }
    }

    private fun loadData(id: String) {
        viewModelScope.launch {
            try {
                val profile = withContext(Dispatchers.IO) { repository.fetchEmployeeProfile(id) }
                _uiState.update {
                    it.copy(
                        profile = profile,
                        profileLoading = false,
                        profileMessage = if (profile == null) "Employee record not found" else null
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG_INVENTORY_VM, "Employee profile load failed: ${e.message}", e)
                _uiState.update {
                    it.copy(profileLoading = false, profileMessage = "Unable to load employee profile")
                }
            }

            try {
                val dates = withContext(Dispatchers.IO) {
                    repository.fetchActivityDates(id, currentMonth.year, currentMonth.monthValue)
                }
                _uiState.update { it.copy(activityDates = dates, activityLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG_INVENTORY_VM, "Activity dates load failed: ${e.message}", e)
                _uiState.update { it.copy(activityDates = emptyList(), activityLoading = false) }
            }

            try {
                val stats = withContext(Dispatchers.IO) { repository.fetchTotalStats(id) }
                _uiState.update { it.copy(totalStats = stats, statsLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG_INVENTORY_VM, "Total stats load failed: ${e.message}", e)
                _uiState.update { it.copy(totalStats = TotalStats(0, 0), statsLoading = false) }
            }
        }
    }

    fun onDaySelected(date: LocalDate) {
        val activeDateSet = _uiState.value.activityDates.toSet()
        val id = userId
        if (id == null || date !in activeDateSet) {
            _uiState.update {
                it.copy(selectedDay = date, dayStats = DayStats(0, 0), dayStatsLoading = false)
            }
            return
        }
        _uiState.update { it.copy(selectedDay = date, dayStats = null, dayStatsLoading = true) }
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val stats = repository.fetchDayStats(id, date)
                _uiState.update { it.copy(dayStats = stats, dayStatsLoading = false) }
            } catch (e: Exception) {
                Log.e(TAG_INVENTORY_VM, "Day stats load failed: ${e.message}", e)
                _uiState.update { it.copy(dayStats = DayStats(0, 0), dayStatsLoading = false) }
            }
        }
    }

    fun onDayDismiss() {
        _uiState.update { it.copy(selectedDay = null, dayStats = null, dayStatsLoading = false) }
    }

    fun refreshStatsSilently() {
        val id = userId ?: return
        viewModelScope.launch {
            try {
                val dates = withContext(Dispatchers.IO) {
                    repository.fetchActivityDates(id, currentMonth.year, currentMonth.monthValue)
                }
                _uiState.update { it.copy(activityDates = dates) }
            } catch (e: Exception) {
                Log.e(TAG_INVENTORY_VM, "Silent activity dates refresh failed: ${e.message}", e)
            }

            try {
                val stats = withContext(Dispatchers.IO) { repository.fetchTotalStats(id) }
                _uiState.update { it.copy(totalStats = stats) }
            } catch (e: Exception) {
                Log.e(TAG_INVENTORY_VM, "Silent stats refresh failed: ${e.message}", e)
            }
        }
    }
}

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