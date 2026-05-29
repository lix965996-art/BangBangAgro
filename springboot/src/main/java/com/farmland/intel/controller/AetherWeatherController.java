package com.farmland.intel.controller;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.farmland.intel.common.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Aether天气控制器 - 使用高德天气API
 */
@RestController
@RequestMapping("/aether/weather")
public class AetherWeatherController {
    
    // JavaScript API密钥（前端地图使用）
    @Value("${amap.js-key:}")
    private String jsKey;

    // Web服务API密钥（后端REST API使用）
    @Value("${amap.web-key:}")
    private String webKey;

    @Value("${amap.js-security-key:}")
    private String jsSecurityKey;
    
    // 默认城市编码（张家界市）
    @Value("${amap.city:430800}")
    private String defaultCity;
    
    // 高德天气API地址
    private static final String AMAP_WEATHER_URL = "https://restapi.amap.com/v3/weather/weatherInfo";
    
    /**
     * 获取高德地图配置（供前端使用）
     */
    @GetMapping("/config")
    public Result getConfig() {
        Map<String, Object> config = new HashMap<>();
        config.put("amapKey", jsKey); // 前端使用JavaScript API Key
        config.put("jsKey", jsKey);
        // webKey 为服务端密钥，不暴露给前端
        config.put("city", defaultCity);
        return Result.success(config);
    }
    
    /**
     * 获取实时天气（高德API）
     */
    @GetMapping("/now")
    public Result getWeatherNow() {
        try {
            // 使用Web服务Key调用REST API
            String apiKey = webKey != null && !webKey.isEmpty() ? webKey : jsKey;
            if (apiKey == null || apiKey.isEmpty()) {
                return Result.success(getMockWeatherNow());
            }
            
            String url = buildWeatherUrl(apiKey, "base");

            String response = HttpUtil.get(url);
            JSONObject json = JSONUtil.parseObj(response);

            if ("1".equals(json.getStr("status"))) {
                JSONArray lives = json.getJSONArray("lives");
                if (lives != null && lives.size() > 0) {
                    JSONObject live = lives.getJSONObject(0);
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    
                    // 转换为前端需要的格式
                    Map<String, Object> data = new HashMap<>();
                    data.put("temp", live.getStr("temperature"));
                    data.put("text", live.getStr("weather"));
                    data.put("humidity", live.getStr("humidity"));
                    data.put("windDir", live.getStr("winddirection"));
                    data.put("windScale", live.getStr("windpower"));
                    data.put("obsTime", live.getStr("reporttime"));
                    
                    result.put("data", data);
                    return Result.success(result);
                }
            }
            return Result.success(getMockWeatherNow());
        } catch (Exception e) {
            return Result.success(getMockWeatherNow());
        }
    }

    /**
     * 获取天气预报（高德API - 未来4天）
     */
    @GetMapping("/7d")
    public Result getWeather7d() {
        try {
            // 使用Web服务Key调用REST API
            String apiKey = webKey != null && !webKey.isEmpty() ? webKey : jsKey;
            if (apiKey == null || apiKey.isEmpty()) {
                return Result.success(getMockWeather7d());
            }
            
            String url = buildWeatherUrl(apiKey, "all");
            String response = HttpUtil.get(url);
            JSONObject json = JSONUtil.parseObj(response);
            
            if ("1".equals(json.getStr("status"))) {
                JSONArray forecasts = json.getJSONArray("forecasts");
                if (forecasts != null && forecasts.size() > 0) {
                    JSONObject forecast = forecasts.getJSONObject(0);
                    JSONArray casts = forecast.getJSONArray("casts");
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    
                    Map<String, Object> data = new HashMap<>();
                    
                    // 转换为前端需要的格式
                    List<Map<String, Object>> daily = new ArrayList<>();
                    List<Map<String, Object>> hourly = new ArrayList<>();
                    
                    if (casts != null) {
                        for (int i = 0; i < casts.size(); i++) {
                            JSONObject cast = casts.getJSONObject(i);
                            Map<String, Object> day = new HashMap<>();
                            day.put("fxDate", cast.getStr("date"));
                            day.put("textDay", cast.getStr("dayweather"));
                            day.put("textNight", cast.getStr("nightweather"));
                            day.put("tempMax", cast.getStr("daytemp"));
                            day.put("tempMin", cast.getStr("nighttemp"));
                            day.put("windDir", cast.getStr("daywind"));
                            day.put("windScale", cast.getStr("daypower"));
                            day.put("week", cast.getStr("week"));
                            daily.add(day);
                            
                            // 生成24小时数据（基于每天的数据）
                            if (i == 0) {
                                int dayTemp = parseIntSafe(cast.getStr("daytemp"), 25);
                                int nightTemp = parseIntSafe(cast.getStr("nighttemp"), 15);
                                for (int h = 0; h < 24; h++) {
                                    Map<String, Object> hour = new HashMap<>();
                                    hour.put("fxTime", cast.getStr("date") + " " + String.format("%02d:00:00", h));
                                    hour.put("text", h < 12 ? cast.getStr("dayweather") : cast.getStr("nightweather"));
                                    // 温度插值：0-14时从夜间低温升至日间高温，14-24时降回夜间低温
                                    int temp;
                                    if (h <= 14) {
                                        temp = (int) Math.round(nightTemp + (dayTemp - nightTemp) * h / 14.0);
                                    } else {
                                        temp = (int) Math.round(dayTemp - (dayTemp - nightTemp) * (h - 14) / 10.0);
                                    }
                                    hour.put("temp", String.valueOf(temp));
                                    hourly.add(hour);
                                }
                            }
                        }
                    }
                    
                    data.put("daily", daily);
                    data.put("hourly", hourly);
                    result.put("data", data);
                    return Result.success(result);
                }
            }
            return Result.success(getMockWeather7d());
        } catch (Exception e) {
            return Result.success(getMockWeather7d());
        }
    }

    /**
     * 获取24小时逐小时天气预报
     */
    @GetMapping("/24h")
    public Result getWeather24h() {
        try {
            String apiKey = webKey != null && !webKey.isEmpty() ? webKey : jsKey;
            if (apiKey == null || apiKey.isEmpty()) {
                return Result.success(getMockWeather24h());
            }
            
            String url = buildWeatherUrl(apiKey, "all");
            String response = HttpUtil.get(url);
            JSONObject json = JSONUtil.parseObj(response);
            
            if ("1".equals(json.getStr("status"))) {
                JSONArray forecasts = json.getJSONArray("forecasts");
                if (forecasts != null && forecasts.size() > 0) {
                    JSONObject forecast = forecasts.getJSONObject(0);
                    JSONArray casts = forecast.getJSONArray("casts");
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("success", true);
                    
                    List<Map<String, Object>> hourly = new ArrayList<>();
                    
                    if (casts != null && casts.size() > 0) {
                        JSONObject today = casts.getJSONObject(0);
                        int dayTemp = parseIntSafe(today.getStr("daytemp"), 25);
                        int nightTemp = parseIntSafe(today.getStr("nighttemp"), 15);
                        String dayWeather = today.getStr("dayweather");
                        String nightWeather = today.getStr("nightweather");
                        
                        // 获取当前小时
                        LocalDateTime now = LocalDateTime.now();
                        int currentHour = now.getHour();

                        // 生成从当前时间开始的24小时预报
                        for (int i = 0; i < 24; i++) {
                            int h = (currentHour + i) % 24;
                            Map<String, Object> hour = new HashMap<>();

                            // 格式化时间
                            LocalDateTime timePoint = now.withHour(h).withMinute(0).withSecond(0).withNano(0);
                            if (h < currentHour) {
                                timePoint = timePoint.plusDays(1);
                            }

                            hour.put("fxTime", timePoint.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00")));
                            hour.put("text", (h >= 6 && h < 18) ? dayWeather : nightWeather);
                            
                            // 更真实的温度变化曲线
                            double tempRange = dayTemp - nightTemp;
                            double temp;
                            if (h >= 6 && h <= 14) {
                                // 6:00-14:00 温度上升
                                temp = nightTemp + tempRange * (h - 6) / 8.0;
                            } else if (h > 14 && h < 22) {
                                // 14:00-22:00 温度下降
                                temp = dayTemp - tempRange * (h - 14) / 8.0;
                            } else {
                                // 22:00-6:00 夜间低温
                                temp = nightTemp + Math.random() * 2;
                            }
                            hour.put("temp", String.valueOf((int) Math.round(temp)));
                            hourly.add(hour);
                        }
                    }
                    
                    result.put("data", hourly);
                    return Result.success(result);
                }
            }
            return Result.success(getMockWeather24h());
        } catch (Exception e) {
            return Result.success(getMockWeather24h());
        }
    }

    /**
     * 获取天气预警（高德API不提供预警，返回空数据）
     */
    @GetMapping("/alerts")
    public Result getWeatherAlerts() {
        // 高德天气API不提供预警信息，返回空数据
        return Result.success(getMockWeatherAlerts());
    }
    
    /**
     * 获取所有天气信息（一次性返回）
     */
    @SuppressWarnings("unchecked")
    @GetMapping("/all")
    public Result getWeatherAll() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        
        Map<String, Object> data = new HashMap<>();
        
        // 获取实时天气
        Result nowResult = getWeatherNow();
        if (nowResult.getData() != null) {
            Map<String, Object> nowData = (Map<String, Object>) nowResult.getData();
            data.put("now", nowData.get("data"));
        }
        
        // 获取7天天气
        Result weekResult = getWeather7d();
        if (weekResult.getData() != null) {
            Map<String, Object> weekData = (Map<String, Object>) weekResult.getData();
            data.put("forecast", weekData.get("data"));
        }
        
        // 获取预警
        Result alertsResult = getWeatherAlerts();
        if (alertsResult.getData() != null) {
            Map<String, Object> alertsData = (Map<String, Object>) alertsResult.getData();
            data.put("alerts", alertsData.get("data"));
        }
        
        result.put("data", data);
        return Result.success(result);
    }
    
    // ========== 模拟数据方法 ==========
    
    private Map<String, Object> getMockWeatherNow() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        
        Map<String, Object> now = new HashMap<>();
        now.put("temp", "25");
        now.put("text", "晴");
        now.put("feelsLike", "26");
        now.put("humidity", "65");
        now.put("windDir", "东南风");
        now.put("windScale", "2");
        now.put("obsTime", new Date().toString());
        
        result.put("data", now);
        return result;
    }
    
    private Map<String, Object> getMockWeather7d() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        
        Map<String, Object> data = new HashMap<>();
        
        // 模拟7天天气
        List<Map<String, Object>> daily = new ArrayList<>();
        String[] weathers = {"晴", "多云", "阴", "小雨"};
        Random random = new Random();
        
        for (int i = 0; i < 7; i++) {
            Map<String, Object> day = new HashMap<>();
            day.put("fxDate", new Date(System.currentTimeMillis() + i * 86400000L).toString());
            day.put("textDay", weathers[random.nextInt(weathers.length)]);
            day.put("tempMax", String.valueOf(25 + random.nextInt(5)));
            day.put("tempMin", String.valueOf(15 + random.nextInt(5)));
            day.put("humidity", String.valueOf(60 + random.nextInt(20)));
            day.put("windDir", "东南风");
            day.put("windScale", String.valueOf(1 + random.nextInt(3)));
            daily.add(day);
        }
        
        // 模拟24小时天气
        List<Map<String, Object>> hourly = new ArrayList<>();
        for (int i = 0; i < 24; i++) {
            Map<String, Object> hour = new HashMap<>();
            hour.put("fxTime", new Date(System.currentTimeMillis() + i * 3600000L).toString());
            hour.put("text", weathers[random.nextInt(weathers.length)]);
            hour.put("temp", String.valueOf(20 + random.nextInt(10)));
            hour.put("humidity", String.valueOf(60 + random.nextInt(20)));
            hour.put("windDir", "东南风");
            hour.put("windLevel", String.valueOf(1 + random.nextInt(3)));
            hourly.add(hour);
        }
        
        data.put("daily", daily);
        data.put("hourly", hourly);
        result.put("data", data);
        
        return result;
    }
    
    private Map<String, Object> getMockWeather24h() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        
        List<Map<String, Object>> hourly = new ArrayList<>();
        String[] weathers = {"晴", "多云", "阴"};
        Random random = new Random();
        LocalDateTime now = LocalDateTime.now();
        int currentHour = now.getHour();

        for (int i = 0; i < 24; i++) {
            int h = (currentHour + i) % 24;
            Map<String, Object> hour = new HashMap<>();

            LocalDateTime timePoint = now.withHour(h).withMinute(0).withSecond(0).withNano(0);
            if (h < currentHour) {
                timePoint = timePoint.plusDays(1);
            }

            hour.put("fxTime", timePoint.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00")));
            hour.put("text", weathers[random.nextInt(weathers.length)]);
            // 模拟温度曲线：夜间低，白天高
            int baseTemp = (h >= 6 && h <= 18) ? 20 : 12;
            hour.put("temp", String.valueOf(baseTemp + random.nextInt(8)));
            hourly.add(hour);
        }
        
        result.put("data", hourly);
        return result;
    }
    
    private Map<String, Object> getMockWeatherAlerts() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("data", new ArrayList<>()); // 无预警
        return result;
    }

    private int parseIntSafe(String str, int defaultValue) {
        if (str == null || str.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String buildWeatherUrl(String apiKey, String extensions) {
        StringBuilder url = new StringBuilder(AMAP_WEATHER_URL)
                .append("?city=").append(defaultCity)
                .append("&key=").append(apiKey)
                .append("&extensions=").append(extensions)
                .append("&sdk=server");
        if ((webKey == null || webKey.isEmpty()) && jsSecurityKey != null && !jsSecurityKey.isEmpty()) {
            url.append("&jscode=").append(jsSecurityKey);
        }
        return url.toString();
    }
}
