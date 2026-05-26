package com.bangbangagro.app.data

import com.google.gson.annotations.SerializedName

// === Unified Response ===
data class ApiResponse<T>(
    @SerializedName("code") val code: String,
    @SerializedName("msg") val msg: String?,
    @SerializedName("data") val data: T?
) {
    val isSuccess get() = code == "200"
}

// === User ===
data class LoginRequest(val username: String, val password: String)

data class UserDTO(
    val id: Int = 0,
    val username: String = "",
    val nickname: String = "",
    val avatarUrl: String? = null,
    val token: String? = null,
    val role: String? = null,
    val menus: List<MenuDTO>? = null
)

data class MenuDTO(
    val id: Int = 0,
    val name: String = "",
    val path: String = "",
    val icon: String? = null,
    val description: String? = null,
    val pid: Int? = null,
    val children: List<MenuDTO>? = null
)

// === Statistic (Farmland) ===
data class Statistic(
    val id: Int = 0,
    val farm: String = "",
    val area: String = "",
    val address: String = "",
    val district: String = "",
    val crop: String = "",
    val number: Int = 0,
    val state: String = "",
    val temperature: Double = 0.0,
    val airhumidity: Double = 0.0,
    val soilhumidity: Double = 0.0,
    val carbon: Double = 0.0,
    val ph: Double = 0.0,
    val light: Double = 0.0,
    val filllight: String = "",
    val monitor: String = "",
    val pump: String = "",
    val keeper: String = "",
    val centerLng: Double = 0.0,
    val centerLat: Double = 0.0,
    val coordinates: String = ""
)

data class DashboardSummary(
    val totalArea: Double = 0.0,
    val totalStock: Double = 0.0,
    val farmCount: Int = 0,
    val normalCount: Int = 0
)

// === IoT Device ===
data class DeviceStatus(
    val success: Boolean = false,
    val online: Boolean = false,
    val temperature: Double = 0.0,
    val humidity: Double = 0.0,
    val led: Int = 0,
    val fan: Boolean = false,
    val pump: Boolean = false,
    @SerializedName("device_name") val deviceName: String = "",
    val source: String = ""
)

data class LedControlRequest(val led: Int)
data class FanControlRequest(val fan: Boolean)
data class BumpControlRequest(val bump: Boolean)

data class ControlResponse(
    val success: Boolean = false,
    val message: String = "",
    val oneNetControlled: Boolean = false
)

// === Weather ===
data class WeatherNowResponse(
    val success: Boolean = false,
    val data: WeatherNow? = null
)

data class WeatherNow(
    val temp: String = "",
    val text: String = "",
    val humidity: String = "",
    val windDir: String = "",
    val windScale: String = "",
    val obsTime: String = ""
)

data class Weather7dResponse(
    val success: Boolean = false,
    val data: Weather7dData? = null
)

data class Weather7dData(
    val daily: List<DailyWeather> = emptyList(),
    val hourly: List<HourlyWeather> = emptyList()
)

data class DailyWeather(
    val fxDate: String = "",
    val textDay: String = "",
    val textNight: String = "",
    val tempMax: String = "",
    val tempMin: String = "",
    val windDir: String = "",
    val windScale: String = "",
    val week: String = ""
)

data class HourlyWeather(
    val fxTime: String = "",
    val text: String = "",
    val temp: String = ""
)

data class WeatherAlertsResponse(
    val success: Boolean = false,
    val data: List<Any> = emptyList()
)

// === Alerts ===
data class FarmlandAlert(
    val id: Int = 0,
    val farmlandId: Int = 0,
    val farmlandName: String = "",
    val alertType: String = "",
    val alertLevel: String = "",
    val currentValue: Double = 0.0,
    val thresholdMin: Double = 0.0,
    val thresholdMax: Double = 0.0,
    val message: String = "",
    val suggestion: String = "",
    val status: String = "pending",
    val createTime: String = "",
    val processTime: String? = null,
    val processor: String? = null
)

// === AI Chat ===
data class ChatRequest(val question: String)
data class ChatResponse(val code: Int = 0, val answer: String = "")

// === Fruit Detection ===
data class FruitDetectResult(
    val total: Int = 0,
    val ripe: Int = 0,
    val unripe: Int = 0,
    val diseased: Int = 0,
    val detections: List<Detection>? = null,
    val imageUrl: String? = null
)

data class Detection(
    val className: String = "",
    val confidence: Double = 0.0,
    val bbox: List<Double>? = null
)

// === Page Response ===
data class PageResponse<T>(
    val records: List<T> = emptyList(),
    val total: Long = 0,
    val size: Long = 0,
    val current: Long = 0,
    val pages: Long = 0
)

// === Sensor Readings ===
data class SensorReadingsResponse(
    val success: Boolean = false,
    val data: List<SensorReading> = emptyList(),
    val days: Int = 0,
    val count: Int = 0
)

data class SensorReading(
    val date: String = "",
    val temp: Double = 0.0,
    val humi: Double = 0.0
)
