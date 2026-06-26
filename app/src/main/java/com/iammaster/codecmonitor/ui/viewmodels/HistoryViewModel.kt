package com.iammaster.codecmonitor.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.iammaster.codecmonitor.data.local.DeviceRef
import com.iammaster.codecmonitor.data.local.HistoryEntity
import com.iammaster.codecmonitor.data.repository.HistoryRange
import com.iammaster.codecmonitor.data.repository.HistoryRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(private val repository: HistoryRepository) : ViewModel() {

    private val _range = MutableStateFlow(HistoryRange.ONE_HOUR)
    val range: StateFlow<HistoryRange> = _range

    private val _selectedMac = MutableStateFlow<String?>(null)
    val selectedMac: StateFlow<String?> = _selectedMac

    private val _deviceOptions = MutableStateFlow<List<DeviceRef>>(emptyList())
    val deviceOptions: StateFlow<List<DeviceRef>> = _deviceOptions

    val points: StateFlow<List<HistoryEntity>> = combine(_range, _selectedMac) { range, mac -> range to mac }
        .flatMapLatest { (range, mac) -> repository.observe(range, mac) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refreshDeviceOptions()
    }

    fun refreshDeviceOptions() {
        viewModelScope.launch {
            _deviceOptions.value = repository.getDistinctDevices()
        }
    }

    fun setRange(range: HistoryRange) {
        _range.value = range
    }

    fun setSelectedMac(mac: String?) {
        _selectedMac.value = mac
    }

    suspend fun exportRows(): List<HistoryEntity> = repository.getSince(_range.value, _selectedMac.value)
}
