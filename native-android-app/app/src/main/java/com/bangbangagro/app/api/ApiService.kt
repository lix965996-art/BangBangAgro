package com.bangbangagro.app.api

import com.bangbangagro.app.data.*
import okhttp3.MultipartBody
import retrofit2.http.*

interface ApiService {

    // === Auth ===
    @POST("/user/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<UserDTO>

    @POST("/user/register")
    suspend fun register(@Body request: LoginRequest): ApiResponse<UserDTO>

    // === Dashboard ===
    @GET("/statistic/dashboard/summary")
    suspend fun getDashboardSummary(): ApiResponse<DashboardSummary>

    @GET("/statistic/dashboard")
    suspend fun getDashboardStats(): ApiResponse<List<Statistic>>

    // === Statistic ===
    @GET("/statistic/page")
    suspend fun getStatisticPage(
        @Query("pageNum") pageNum: Int,
        @Query("pageSize") pageSize: Int,
        @Query("farm") farm: String? = null
    ): ApiResponse<PageResponse<Statistic>>

    // === IoT Device ===
    @GET("/aether/device/status")
    suspend fun getDeviceStatus(): ApiResponse<DeviceStatus>

    @POST("/aether/device/control/led")
    suspend fun controlLed(@Body request: LedControlRequest): ApiResponse<ControlResponse>

    @POST("/aether/device/control/fan")
    suspend fun controlFan(@Body request: FanControlRequest): ApiResponse<ControlResponse>

    @POST("/aether/device/control/bump")
    suspend fun controlBump(@Body request: BumpControlRequest): ApiResponse<ControlResponse>

    @GET("/aether/readings/detail")
    suspend fun getSensorReadings(@Query("days") days: Int = 7): ApiResponse<SensorReadingsResponse>

    // === Weather ===
    @GET("/aether/weather/now")
    suspend fun getWeatherNow(): ApiResponse<WeatherNowResponse>

    @GET("/aether/weather/7d")
    suspend fun getWeather7d(): ApiResponse<Weather7dResponse>

    @GET("/aether/weather/24h")
    suspend fun getWeather24h(): ApiResponse<Weather24hResponse>

    @GET("/aether/weather/alerts")
    suspend fun getWeatherAlerts(): ApiResponse<WeatherAlertsResponse>

    // === Alerts ===
    @GET("/alert/today/tasks")
    suspend fun getTodayAlerts(): ApiResponse<List<FarmlandAlert>>

    @GET("/alert/pending")
    suspend fun getPendingAlerts(): ApiResponse<List<FarmlandAlert>>

    @POST("/alert/{id}/process")
    suspend fun processAlert(@Path("id") id: Int): ApiResponse<Any?>

    // === Fruit Detection ===
    @Multipart
    @POST("/fruit-detect/analyze")
    suspend fun detectFruit(@Part file: MultipartBody.Part): ApiResponse<FruitDetectResult>

    // === AI Chat ===
    @POST("/api/chat/ask")
    suspend fun chat(@Body request: ChatRequest): ChatResponse
}

// Additional weather response for 24h
data class Weather24hResponse(
    val success: Boolean = false,
    val data: List<HourlyWeather> = emptyList()
)
