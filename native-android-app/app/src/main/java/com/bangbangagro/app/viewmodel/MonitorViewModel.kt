package com.bangbangagro.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bangbangagro.app.api.RetrofitClient
import com.bangbangagro.app.data.BumpControlRequest
import com.bangbangagro.app.data.DailyWeather
import com.bangbangagro.app.data.DeviceStatus
import com.bangbangagro.app.data.FanControlRequest
import com.bangbangagro.app.data.HourlyWeather
import com.bangbangagro.app.data.LedControlRequest
import com.bangbangagro.app.data.WeatherNow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MonitorViewModel : ViewModel() {

    private val _deviceStatus = MutableStateFlow<DeviceStatus?>(null)
    val deviceStatus: StateFlow<DeviceStatus?> = _deviceStatus

    private val _weatherNow = MutableStateFlow<WeatherNow?>(null)
    val weatherNow: StateFlow<WeatherNow?> = _weatherNow

    private val _dailyForecast = MutableStateFlow<List<DailyWeather>>(emptyList())
    val dailyForecast: StateFlow<List<DailyWeather>> = _dailyForecast

    private val _hourlyForecast = MutableStateFlow<List<HourlyWeather>>(emptyList())
    val hourlyForecast: StateFlow<List<HourlyWeather>> = _hourlyForecast

    private val _controlMessage = MutableStateFlow<String?>(null)
    val controlMessage: StateFlow<String?> = _controlMessage

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    init {
        loadAll()
    }

    fun loadAll() {
        viewModelScope.launch {
            _loading.value = true
            try {
                val deviceResp = RetrofitClient.api.getDeviceStatus()
                if (deviceResp.isSuccess) _deviceStatus.value = deviceResp.data

                val weatherResp = RetrofitClient.api.getWeatherNow()
                if (weatherResp.isSuccess) _weatherNow.value = weatherResp.data?.data

                val forecastResp = RetrofitClient.api.getWeather7d()
                if (forecastResp.isSuccess) {
                    _dailyForecast.value = forecastResp.data?.data?.daily.orEmpty()
                    _hourlyForecast.value = forecastResp.data?.data?.hourly.orEmpty()
                }
            } catch (e: Exception) {
                _controlMessage.value = "数据加载失败：${e.message}"
            } finally {
                _loading.value = false
            }
        }
    }

    fun controlLed(on: Boolean) = control("补光灯") {
        RetrofitClient.api.controlLed(LedControlRequest(if (on) 1 else 0)).data?.message
    }

    fun controlFan(on: Boolean) = control("风扇") {
        RetrofitClient.api.controlFan(FanControlRequest(on)).data?.message
    }

    fun controlPump(on: Boolean) = control("水泵") {
        RetrofitClient.api.controlBump(BumpControlRequest(on)).data?.message
    }

    private fun control(name: String, block: suspend () -> String?) {
        viewModelScope.launch {
            try {
                _controlMessage.value = block() ?: "$name 控制指令已发送"
                loadAll()
            } catch (e: Exception) {
                _controlMessage.value = "$name 控制失败：${e.message}"
            }
        }
    }

    fun clearMessage() {
        _controlMessage.value = null
    }
}
