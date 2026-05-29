package com.farmland.intel.service.impl;

import cn.hutool.http.HttpUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.farmland.intel.entity.Statistic;
import com.farmland.intel.mapper.StatisticMapper;
import com.farmland.intel.service.IAgriDailyReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * 智能体农情日报服务实现
 */
@Service
@SuppressWarnings("unchecked")
public class AgriDailyReportServiceImpl implements IAgriDailyReportService {

    private static final Logger log = LoggerFactory.getLogger(AgriDailyReportServiceImpl.class);
    
    @Autowired
    private StatisticMapper statisticMapper;
    
    @Value("${amap.web-key:}")
    private String amapWebKey;

    @Value("${amap.js-security-key:}")
    private String amapJsSecurityKey;
    
    @Value("${amap.city:430800}")
    private String defaultCity;
    
    private static final String AMAP_WEATHER_URL = "https://restapi.amap.com/v3/weather/weatherInfo";
    
    @Override
    public Map<String, Object> generateDailyReport(Integer userId, Integer farmlandId) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            // 1. 获取天气数据
            Map<String, Object> weatherData = getWeatherData();
            result.put("weather", weatherData);
            
            // 2. 获取农田数据
            List<Statistic> farmlands = getFarmlandData(farmlandId);
            result.put("farmlands", farmlands);
            
            // 3. 生成智能分析文本
            String aiAdvice = generateAIAdvice(weatherData, farmlands);
            result.put("aiAdvice", aiAdvice);
            
            // 4. 生成农事建议列表
            List<Map<String, Object>> suggestions = generateSuggestions(weatherData, farmlands);
            result.put("suggestions", suggestions);
            
            // 5. 添加时间戳
            result.put("generateTime", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
            result.put("success", true);
            
        } catch (Exception e) {
            log.error("生成农情日报失败", e);
            result.put("success", false);
            result.put("error", "农情日报生成失败");
            result.put("aiAdvice", generateDefaultAdvice());
        }
        
        return result;
    }
    
    @Override
    public Map<String, Object> getWeatherAnalysis() {
        Map<String, Object> weatherData = getWeatherData();
        Map<String, Object> analysis = new HashMap<>();
        
        if (weatherData.containsKey("current")) {
            Map<String, Object> current = (Map<String, Object>) weatherData.get("current");
            analysis.put("temperature", current.get("temperature"));
            analysis.put("weather", current.get("weather"));
            analysis.put("humidity", current.get("humidity"));
            analysis.put("windpower", current.get("windpower"));
        }
        
        // 添加天气分析建议
        analysis.put("analysis", analyzeWeatherForAgriculture(weatherData));
        
        return analysis;
    }
    
    @Override
    public String generateFarmlandAdvice(Integer farmlandId) {
        List<Statistic> farmlands = getFarmlandData(farmlandId);
        Map<String, Object> weatherData = getWeatherData();
        
        if (!farmlands.isEmpty()) {
            Statistic farmland = farmlands.get(0);
            return generateSpecificFarmlandAdvice(farmland, weatherData);
        }
        
        return "暂无特定农田数据，请确保农田信息已录入系统。";
    }
    
    /**
     * 获取天气数据
     */
    private Map<String, Object> getWeatherData() {
        Map<String, Object> weatherData = new HashMap<>();
        
        try {
            if (amapWebKey != null && !amapWebKey.isEmpty()) {
                // 获取实时天气
                String url = buildWeatherUrl("base");
                String response = HttpUtil.get(url);
                JSONObject json = JSONUtil.parseObj(response);
                
                if ("1".equals(json.getStr("status"))) {
                    JSONArray lives = json.getJSONArray("lives");
                    if (lives != null && lives.size() > 0) {
                        JSONObject live = lives.getJSONObject(0);
                        Map<String, Object> current = new HashMap<>();
                        current.put("temperature", live.getStr("temperature"));
                        current.put("weather", live.getStr("weather"));
                        current.put("humidity", live.getStr("humidity"));
                        current.put("winddirection", live.getStr("winddirection"));
                        current.put("windpower", live.getStr("windpower"));
                        current.put("reporttime", live.getStr("reporttime"));
                        weatherData.put("current", current);
                    }
                }
                
                // 获取天气预报
                String forecastUrl = buildWeatherUrl("all");
                String forecastResponse = HttpUtil.get(forecastUrl);
                JSONObject forecastJson = JSONUtil.parseObj(forecastResponse);
                
                if ("1".equals(forecastJson.getStr("status"))) {
                    JSONArray forecasts = forecastJson.getJSONArray("forecasts");
                    if (forecasts != null && forecasts.size() > 0) {
                        JSONObject forecast = forecasts.getJSONObject(0);
                        JSONArray casts = forecast.getJSONArray("casts");
                        weatherData.put("forecast", casts);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取高德天气数据失败，使用默认天气数据", e);
        }
        
        // 如果获取失败，返回默认数据
        if (weatherData.isEmpty()) {
            Map<String, Object> current = new HashMap<>();
            current.put("temperature", "26");
            current.put("weather", "晴");
            current.put("humidity", "65");
            current.put("winddirection", "东南");
            current.put("windpower", "≤3");
            weatherData.put("current", current);
        }
        
        return weatherData;
    }
    
    /**
     * 获取农田数据
     */
    private List<Statistic> getFarmlandData(Integer farmlandId) {
        LambdaQueryWrapper<Statistic> wrapper = new LambdaQueryWrapper<>();
        if (farmlandId != null) {
            wrapper.eq(Statistic::getId, farmlandId);
        }
        return statisticMapper.selectPage(new Page<>(1, 5), wrapper).getRecords();
    }
    
    /**
     * 生成AI农情建议
     */
    private String generateAIAdvice(Map<String, Object> weatherData, List<Statistic> farmlands) {
        StringBuilder advice = new StringBuilder();
        
        // 分析天气
        if (weatherData.containsKey("current")) {
            Map<String, Object> current = (Map<String, Object>) weatherData.get("current");
            String temp = (String) current.get("temperature");
            String weather = (String) current.get("weather");
            String humidity = (String) current.get("humidity");
            
            advice.append("今日").append(weather).append("，气温").append(temp).append("°C，");
            advice.append("空气湿度").append(humidity).append("%。");
            
            // 根据天气生成建议
            if (weather.contains("雨")) {
                advice.append("有降雨，建议暂停灌溉作业，注意排水防涝。");
            } else if (weather.contains("晴") || weather.contains("多云")) {
                int tempInt = parseIntSafe(temp, 25);
                int humInt = parseIntSafe(humidity, 65);
                
                if (tempInt > 30) {
                    advice.append("气温较高，建议增加灌溉频率，开启遮阳设施。");
                } else if (tempInt < 15) {
                    advice.append("气温偏低，注意作物防冻，可考虑开启补光设施。");
                } else {
                    advice.append("天气条件良好，适宜进行农事作业。");
                }
                
                if (humInt < 40) {
                    advice.append("空气干燥，建议适当增加喷灌。");
                }
            }
        }
        
        // 分析农田状态
        if (!farmlands.isEmpty()) {
            advice.append("\n");
            for (Statistic farmland : farmlands) {
                if (farmland.getSoilhumidity() != null && farmland.getSoilhumidity() < 30) {
                    advice.append(farmland.getFarm()).append("土壤湿度偏低，建议及时灌溉。");
                }
                if (farmland.getPh() != null) {
                    BigDecimal ph = farmland.getPh();
                    if (ph.compareTo(new BigDecimal("6.0")) < 0) {
                        advice.append(farmland.getFarm()).append("土壤偏酸性，考虑施用石灰调节。");
                    } else if (ph.compareTo(new BigDecimal("7.5")) > 0) {
                        advice.append(farmland.getFarm()).append("土壤偏碱性，建议施用有机肥改良。");
                    }
                }
                
                // 根据作物类型给出建议
                if (farmland.getCrop() != null) {
                    String crop = farmland.getCrop();
                    String state = farmland.getState();
                    advice.append(generateCropSpecificAdvice(crop, state, weatherData));
                }
            }
        } else {
            advice.append("建议完善农田信息，以获得更精准的农事指导。");
        }
        
        // 预报分析
        if (weatherData.containsKey("forecast")) {
            JSONArray forecast = (JSONArray) weatherData.get("forecast");
            if (forecast != null && forecast.size() > 0) {
                JSONObject tomorrow = forecast.getJSONObject(1);
                if (tomorrow != null) {
                    String dayweather = tomorrow.getStr("dayweather");
                    if (dayweather.contains("雨")) {
                        advice.append("明日有雨，请提前做好防雨准备。");
                    }
                }
            }
        }
        
        return advice.toString();
    }
    
    /**
     * 生成作物特定建议
     */
    private String generateCropSpecificAdvice(String crop, String state, Map<String, Object> weatherData) {
        StringBuilder advice = new StringBuilder();
        
        // 根据不同作物类型生成建议
        switch (crop) {
            case "番茄":
            case "西红柿":
                if ("成熟期".equals(state)) {
                    advice.append("番茄进入成熟期，注意及时采收，避免过熟裂果。");
                } else if ("生长期".equals(state)) {
                    advice.append("番茄生长期需充足光照，建议保持土壤湿润。");
                }
                break;
            case "黄瓜":
                advice.append("黄瓜喜湿润环境，建议保持土壤湿度60-70%。");
                break;
            case "草莓":
                if ("花期".equals(state)) {
                    advice.append("草莓花期需控制湿度，避免病害。");
                } else if ("果期".equals(state)) {
                    advice.append("草莓果期注意防鸟，保持通风。");
                }
                break;
            case "水稻":
                if ("抽穗期".equals(state)) {
                    advice.append("水稻抽穗期需保持浅水层，注意防治病虫害。");
                }
                break;
            default:
                advice.append("注意观察").append(crop).append("生长状况。");
        }
        
        return advice.toString();
    }
    
    /**
     * 生成建议列表
     */
    private List<Map<String, Object>> generateSuggestions(Map<String, Object> weatherData, List<Statistic> farmlands) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        
        // 基于天气的建议
        if (weatherData.containsKey("current")) {
            Map<String, Object> current = (Map<String, Object>) weatherData.get("current");
            String weather = (String) current.get("weather");
            
            Map<String, Object> weatherSugg = new HashMap<>();
            weatherSugg.put("type", "weather");
            weatherSugg.put("title", "天气建议");
            weatherSugg.put("content", getWeatherSuggestion(weather));
            weatherSugg.put("priority", getPriorityByWeather(weather));
            suggestions.add(weatherSugg);
        }
        
        // 基于农田的建议
        for (Statistic farmland : farmlands) {
            if (farmland.getSoilhumidity() != null && farmland.getSoilhumidity() < 30) {
                Map<String, Object> soilSugg = new HashMap<>();
                soilSugg.put("type", "soil");
                soilSugg.put("title", farmland.getFarm() + " - 土壤湿度");
                soilSugg.put("content", "土壤湿度偏低(" + farmland.getSoilhumidity() + "%)，建议开启灌溉系统");
                soilSugg.put("priority", "high");
                suggestions.add(soilSugg);
            }
            
            if (farmland.getTemperature() != null) {
                BigDecimal temp = farmland.getTemperature();
                if (temp.compareTo(new BigDecimal("35")) > 0) {
                    Map<String, Object> tempSugg = new HashMap<>();
                    tempSugg.put("type", "temperature");
                    tempSugg.put("title", farmland.getFarm() + " - 高温预警");
                    tempSugg.put("content", "温度过高(" + temp + "°C)，建议开启遮阳和通风设备");
                    tempSugg.put("priority", "high");
                    suggestions.add(tempSugg);
                }
            }
        }
        
        return suggestions;
    }
    
    /**
     * 获取天气建议
     */
    private String getWeatherSuggestion(String weather) {
        if (weather.contains("雨")) {
            return "今日有雨，注意排水，暂停灌溉";
        } else if (weather.contains("晴")) {
            return "天气晴好，适宜进行施肥、除草等农事活动";
        } else if (weather.contains("阴")) {
            return "阴天光照不足，可考虑开启补光设备";
        } else if (weather.contains("雪")) {
            return "注意防寒保暖，检查大棚设施";
        }
        return "天气正常，按计划进行农事作业";
    }
    
    /**
     * 根据天气获取优先级
     */
    private String getPriorityByWeather(String weather) {
        if (weather.contains("暴雨") || weather.contains("大雨") || weather.contains("雪")) {
            return "high";
        } else if (weather.contains("雨")) {
            return "medium";
        }
        return "low";
    }
    
    /**
     * 分析天气对农业的影响
     */
    private String analyzeWeatherForAgriculture(Map<String, Object> weatherData) {
        StringBuilder analysis = new StringBuilder();
        
        if (weatherData.containsKey("current")) {
            Map<String, Object> current = (Map<String, Object>) weatherData.get("current");
            String temp = (String) current.get("temperature");
            String humidity = (String) current.get("humidity");
            String windpower = (String) current.get("windpower");
            
            int tempInt = parseIntSafe(temp, 25);
            int humInt = parseIntSafe(humidity, 65);
            
            // 温度分析
            if (tempInt >= 35) {
                analysis.append("高温天气，作物易失水，需增加灌溉频率；");
            } else if (tempInt >= 25 && tempInt < 35) {
                analysis.append("温度适宜，有利于作物生长；");
            } else if (tempInt >= 15 && tempInt < 25) {
                analysis.append("温度偏低，部分热带作物生长缓慢；");
            } else {
                analysis.append("低温天气，需注意防寒保暖；");
            }
            
            // 湿度分析
            if (humInt >= 80) {
                analysis.append("湿度较高，注意防治病虫害；");
            } else if (humInt >= 60 && humInt < 80) {
                analysis.append("湿度适中，有利于作物生长；");
            } else {
                analysis.append("空气干燥，注意补充水分；");
            }
            
            // 风力分析
            if (windpower.contains("4") || windpower.contains("5") || windpower.contains("6")) {
                analysis.append("风力较大，注意固定农业设施。");
            }
        }
        
        return analysis.toString();
    }
    
    /**
     * 生成特定农田建议
     */
    private String generateSpecificFarmlandAdvice(Statistic farmland, Map<String, Object> weatherData) {
        StringBuilder advice = new StringBuilder();
        
        advice.append("【").append(farmland.getFarm()).append("】农田分析：\n");
        advice.append("种植作物：").append(farmland.getCrop()).append("，");
        advice.append("生长状态：").append(farmland.getState()).append("\n");
        
        // 环境数据分析
        if (farmland.getTemperature() != null) {
            advice.append("当前温度：").append(farmland.getTemperature()).append("°C，");
        }
        if (farmland.getSoilhumidity() != null) {
            advice.append("土壤湿度：").append(farmland.getSoilhumidity()).append("%\n");
        }
        
        // 生成具体建议
        if (farmland.getSoilhumidity() != null && farmland.getSoilhumidity() < 40) {
            advice.append("建议：土壤湿度偏低，需要及时灌溉。");
        }
        if (farmland.getPh() != null) {
            BigDecimal ph = farmland.getPh();
            if (ph.compareTo(new BigDecimal("6.5")) < 0 || ph.compareTo(new BigDecimal("7.5")) > 0) {
                advice.append("土壤pH值为").append(ph).append("，建议调节至6.5-7.5之间。");
            }
        }
        
        return advice.toString();
    }
    
    /**
     * 生成默认建议
     */
    private String generateDefaultAdvice() {
        return "智能农情分析系统正在初始化，请确保已配置天气API密钥并录入农田信息。建议定期巡检农田，关注作物生长状况，及时调整农事计划。";
    }

    private int parseIntSafe(String str, int defaultValue) {
        if (str == null || str.isEmpty()) return defaultValue;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private String buildWeatherUrl(String extensions) {
        StringBuilder url = new StringBuilder(AMAP_WEATHER_URL)
                .append("?city=").append(defaultCity)
                .append("&key=").append(amapWebKey)
                .append("&extensions=").append(extensions)
                .append("&sdk=server");
        if (amapJsSecurityKey != null && !amapJsSecurityKey.isEmpty()) {
            url.append("&jscode=").append(amapJsSecurityKey);
        }
        return url.toString();
    }
}
