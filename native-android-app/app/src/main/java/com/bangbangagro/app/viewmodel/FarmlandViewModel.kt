package com.bangbangagro.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangbangagro.app.api.RetrofitClient
import com.bangbangagro.app.data.Statistic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class FarmlandViewModel : ViewModel() {

    private val _farmlands = MutableStateFlow<List<Statistic>>(emptyList())
    val farmlands: StateFlow<List<Statistic>> = _farmlands

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    private val _selectedFarmland = MutableStateFlow<Statistic?>(null)
    val selectedFarmland: StateFlow<Statistic?> = _selectedFarmland

    init {
        loadFarmlands()
    }

    fun loadFarmlands() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val resp = RetrofitClient.api.getDashboardStats()
                if (resp.isSuccess) {
                    _farmlands.value = resp.data ?: emptyList()
                }
            } catch (_: Exception) {}
            _loading.value = false
        }
    }

    fun selectFarmland(stat: Statistic) {
        _selectedFarmland.value = stat
    }

    fun clearSelection() {
        _selectedFarmland.value = null
    }
}
