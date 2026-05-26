package com.bangbangagro.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangbangagro.app.api.RetrofitClient
import com.bangbangagro.app.data.FarmlandAlert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class AlertViewModel : ViewModel() {

    private val _alerts = MutableStateFlow<List<FarmlandAlert>>(emptyList())
    val alerts: StateFlow<List<FarmlandAlert>> = _alerts

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    init {
        loadAlerts()
    }

    fun loadAlerts() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val resp = RetrofitClient.api.getPendingAlerts()
                if (resp.isSuccess) {
                    _alerts.value = resp.data ?: emptyList()
                }
            } catch (_: Exception) {}
            _loading.value = false
        }
    }

    fun processAlert(id: Int) {
        viewModelScope.launch {
            try {
                RetrofitClient.api.processAlert(id)
                loadAlerts()
            } catch (_: Exception) {}
        }
    }
}
