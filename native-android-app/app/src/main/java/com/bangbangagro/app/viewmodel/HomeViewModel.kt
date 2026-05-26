package com.bangbangagro.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangbangagro.app.api.RetrofitClient
import com.bangbangagro.app.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel : ViewModel() {

    private val _summary = MutableStateFlow<DashboardSummary?>(null)
    val summary: StateFlow<DashboardSummary?> = _summary

    private val _stats = MutableStateFlow<List<Statistic>>(emptyList())
    val stats: StateFlow<List<Statistic>> = _stats

    private val _deviceStatus = MutableStateFlow<DeviceStatus?>(null)
    val deviceStatus: StateFlow<DeviceStatus?> = _deviceStatus

    private val _todayAlerts = MutableStateFlow<List<FarmlandAlert>>(emptyList())
    val todayAlerts: StateFlow<List<FarmlandAlert>> = _todayAlerts

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    init {
        loadDashboard()
    }

    fun loadDashboard() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val summaryResp = RetrofitClient.api.getDashboardSummary()
                if (summaryResp.isSuccess) _summary.value = summaryResp.data

                val statsResp = RetrofitClient.api.getDashboardStats()
                if (statsResp.isSuccess) _stats.value = statsResp.data ?: emptyList()

                val deviceResp = RetrofitClient.api.getDeviceStatus()
                if (deviceResp.isSuccess) _deviceStatus.value = deviceResp.data

                val alertsResp = RetrofitClient.api.getTodayAlerts()
                if (alertsResp.isSuccess) _todayAlerts.value = alertsResp.data ?: emptyList()
            } catch (_: Exception) {}
            _loading.value = false
        }
    }
}
