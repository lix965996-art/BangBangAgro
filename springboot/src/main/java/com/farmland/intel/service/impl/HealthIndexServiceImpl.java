package com.farmland.intel.service.impl;

import com.farmland.intel.entity.FarmlandAlert;
import com.farmland.intel.entity.HealthIndexConfig;
import com.farmland.intel.entity.Statistic;
import com.farmland.intel.mapper.FarmlandAlertMapper;
import com.farmland.intel.mapper.HealthIndexConfigMapper;
import com.farmland.intel.service.IHealthIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康指数服务实现类
 */
@Service
public class HealthIndexServiceImpl implements IHealthIndexService {

    @Autowired
    private HealthIndexConfigMapper configMapper;

    @Autowired
    private FarmlandAlertMapper alertMapper;

    @Override
    public Integer calculateHealthIndex(Statistic statistic) {
        if (statistic == null) {
            return 0;
        }

        // 获取所有配置
        List<HealthIndexConfig> configs = configMapper.selectList(null);
        Map<String, HealthIndexConfig> configMap = new HashMap<>();
        for (HealthIndexConfig config : configs) {
            configMap.put(config.getIndicator(), config);
        }

        // 如果没有配置，使用默认配置
        if (configMap.isEmpty()) {
            return calculateWithDefaultConfig(statistic);
        }

        double totalScore = 0.0;
        double totalWeight = 0.0;

        // 温度评分
        if (statistic.getTemperature() != null && configMap.containsKey("temperature")) {
            HealthIndexConfig config = configMap.get("temperature");
            double score = calculateIndicatorScore(
                statistic.getTemperature().doubleValue(),
                config.getExcellentMin().doubleValue(),
                config.getExcellentMax().doubleValue(),
                config.getGoodMin().doubleValue(),
                config.getGoodMax().doubleValue()
            );
            double weight = config.getWeight().doubleValue();
            totalScore += score * weight;
            totalWeight += weight;
        }

        // 空气湿度评分
        if (statistic.getAirhumidity() != null && configMap.containsKey("air_humidity")) {
            HealthIndexConfig config = configMap.get("air_humidity");
            double score = calculateIndicatorScore(
                statistic.getAirhumidity().doubleValue(),
                config.getExcellentMin().doubleValue(),
                config.getExcellentMax().doubleValue(),
                config.getGoodMin().doubleValue(),
                config.getGoodMax().doubleValue()
            );
            double weight = config.getWeight().doubleValue();
            totalScore += score * weight;
            totalWeight += weight;
        }

        // 土壤湿度评分
        if (statistic.getSoilhumidity() != null && configMap.containsKey("soil_humidity")) {
            HealthIndexConfig config = configMap.get("soil_humidity");
            double score = calculateIndicatorScore(
                statistic.getSoilhumidity().doubleValue(),
                config.getExcellentMin().doubleValue(),
                config.getExcellentMax().doubleValue(),
                config.getGoodMin().doubleValue(),
                config.getGoodMax().doubleValue()
            );
            double weight = config.getWeight().doubleValue();
            totalScore += score * weight;
            totalWeight += weight;
        }

        // CO2评分
        if (statistic.getCarbon() != null && configMap.containsKey("carbon")) {
            HealthIndexConfig config = configMap.get("carbon");
            double score = calculateIndicatorScore(
                statistic.getCarbon().doubleValue(),
                config.getExcellentMin().doubleValue(),
                config.getExcellentMax().doubleValue(),
                config.getGoodMin().doubleValue(),
                config.getGoodMax().doubleValue()
            );
            double weight = config.getWeight().doubleValue();
            totalScore += score * weight;
            totalWeight += weight;
        }

        // pH评分
        if (statistic.getPh() != null && configMap.containsKey("ph")) {
            HealthIndexConfig config = configMap.get("ph");
            double score = calculateIndicatorScore(
                statistic.getPh().doubleValue(),
                config.getExcellentMin().doubleValue(),
                config.getExcellentMax().doubleValue(),
                config.getGoodMin().doubleValue(),
                config.getGoodMax().doubleValue()
            );
            double weight = config.getWeight().doubleValue();
            totalScore += score * weight;
            totalWeight += weight;
        }

        // 光照评分
        if (statistic.getLight() != null && configMap.containsKey("light")) {
            HealthIndexConfig config = configMap.get("light");
            double score = calculateIndicatorScore(
                statistic.getLight().doubleValue(),
                config.getExcellentMin().doubleValue(),
                config.getExcellentMax().doubleValue(),
                config.getGoodMin().doubleValue(),
                config.getGoodMax().doubleValue()
            );
            double weight = config.getWeight().doubleValue();
            totalScore += score * weight;
            totalWeight += weight;
        }

        if (totalWeight == 0) {
            return 0;
        }

        int healthIndex = (int) Math.round(totalScore / totalWeight);
        return Math.max(0, Math.min(100, healthIndex));
    }

    /**
     * 使用默认配置计算健康指数
     */
    private Integer calculateWithDefaultConfig(Statistic statistic) {
        double totalScore = 0.0;
        int count = 0;

        // 温度评分（20-28优秀，15-32良好）
        if (statistic.getTemperature() != null) {
            double temp = statistic.getTemperature().doubleValue();
            if (temp >= 20 && temp <= 28) totalScore += 100;
            else if (temp >= 15 && temp <= 32) totalScore += 75;
            else totalScore += 50;
            count++;
        }

        // 空气湿度评分（60-80优秀，50-90良好）
        if (statistic.getAirhumidity() != null) {
            double airHum = statistic.getAirhumidity().doubleValue();
            if (airHum >= 60 && airHum <= 80) totalScore += 100;
            else if (airHum >= 50 && airHum <= 90) totalScore += 75;
            else totalScore += 50;
            count++;
        }

        // 土壤湿度评分（40-70优秀，30-80良好）
        if (statistic.getSoilhumidity() != null) {
            double soilHum = statistic.getSoilhumidity().doubleValue();
            if (soilHum >= 40 && soilHum <= 70) totalScore += 100;
            else if (soilHum >= 30 && soilHum <= 80) totalScore += 75;
            else totalScore += 50;
            count++;
        }

        // CO2评分（350-450优秀，300-1500良好）
        if (statistic.getCarbon() != null) {
            double carbon = statistic.getCarbon().doubleValue();
            if (carbon >= 350 && carbon <= 450) totalScore += 100;
            else if (carbon >= 300 && carbon <= 1500) totalScore += 75;
            else totalScore += 50;
            count++;
        }

        // pH评分（6.0-7.5优秀，5.5-8.0良好）
        if (statistic.getPh() != null) {
            double ph = statistic.getPh().doubleValue();
            if (ph >= 6.0 && ph <= 7.5) totalScore += 100;
            else if (ph >= 5.5 && ph <= 8.0) totalScore += 75;
            else totalScore += 50;
            count++;
        }

        // 光照评分（2000-3000优秀，1500-3500良好）
        if (statistic.getLight() != null) {
            double light = statistic.getLight().doubleValue();
            if (light >= 2000 && light <= 3000) totalScore += 100;
            else if (light >= 1500 && light <= 3500) totalScore += 75;
            else totalScore += 50;
            count++;
        }

        if (count == 0) {
            return 0;
        }

        int healthIndex = (int) Math.round(totalScore / count);
        return Math.max(0, Math.min(100, healthIndex));
    }

    /**
     * 计算单个指标的评分（0-100）
     */
    private double calculateIndicatorScore(double value, double excellentMin, double excellentMax,
                                           double goodMin, double goodMax) {
        if (value >= excellentMin && value <= excellentMax) {
            return 100.0;
        } else if (value >= goodMin && value <= goodMax) {
            return 75.0;
        } else {
            return 50.0;
        }
    }

    @Override
    @Transactional
    public void checkAndGenerateAlerts(Statistic statistic) {
        if (statistic == null || statistic.getId() == null) {
            return;
        }

        // 获取配置
        List<HealthIndexConfig> configs = configMapper.selectList(null);
        Map<String, HealthIndexConfig> configMap = new HashMap<>();
        for (HealthIndexConfig config : configs) {
            configMap.put(config.getIndicator(), config);
        }

        // 检查每个指标
        checkIndicatorAlert(statistic, "temperature", 
            statistic.getTemperature() != null ? statistic.getTemperature().doubleValue() : null,
            configMap.get("temperature"), "温度");
        
        checkIndicatorAlert(statistic, "air_humidity",
            statistic.getAirhumidity() != null ? statistic.getAirhumidity().doubleValue() : null,
            configMap.get("air_humidity"), "空气湿度");
        
        checkIndicatorAlert(statistic, "soil_humidity",
            statistic.getSoilhumidity() != null ? statistic.getSoilhumidity().doubleValue() : null,
            configMap.get("soil_humidity"), "土壤湿度");
        
        checkIndicatorAlert(statistic, "carbon",
            statistic.getCarbon() != null ? statistic.getCarbon().doubleValue() : null,
            configMap.get("carbon"), "CO2含量");
        
        checkIndicatorAlert(statistic, "ph",
            statistic.getPh() != null ? statistic.getPh().doubleValue() : null,
            configMap.get("ph"), "pH值");
        
        checkIndicatorAlert(statistic, "light",
            statistic.getLight() != null ? statistic.getLight().doubleValue() : null,
            configMap.get("light"), "光照强度");
    }

    /**
     * 检查单个指标并生成预警
     */
    private void checkIndicatorAlert(Statistic statistic, String alertType, Double currentValue,
                                     HealthIndexConfig config, String indicatorName) {
        if (currentValue == null || config == null) {
            return;
        }

        boolean needAlert = false;
        String alertLevel = "low";
        String message = "";
        String suggestion = "";

        // 检查是否超出阈值
        if (config.getThresholdMin() != null && currentValue < config.getThresholdMin().doubleValue()) {
            needAlert = true;
            alertLevel = currentValue < config.getThresholdMin().doubleValue() * 0.8 ? "high" : "medium";
            message = String.format("%s当前值%.2f，低于预警下限%.2f", indicatorName, currentValue, config.getThresholdMin().doubleValue());
            suggestion = generateSuggestion(alertType, currentValue, config.getThresholdMin().doubleValue(), true);
        } else if (config.getThresholdMax() != null && currentValue > config.getThresholdMax().doubleValue()) {
            needAlert = true;
            alertLevel = currentValue > config.getThresholdMax().doubleValue() * 1.2 ? "high" : "medium";
            message = String.format("%s当前值%.2f，超过预警上限%.2f", indicatorName, currentValue, config.getThresholdMax().doubleValue());
            suggestion = generateSuggestion(alertType, currentValue, config.getThresholdMax().doubleValue(), false);
        }

        if (needAlert) {
            // 检查是否已存在未处理的相同预警
            FarmlandAlert existingAlert = alertMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<FarmlandAlert>()
                    .eq("farmland_id", statistic.getId())
                    .eq("alert_type", alertType)
                    .eq("status", "pending")
                    .orderByDesc("create_time")
            );

            if (existingAlert == null) {
                // 创建新预警
                FarmlandAlert alert = new FarmlandAlert();
                alert.setFarmlandId(statistic.getId());
                alert.setFarmlandName(statistic.getFarm());
                alert.setAlertType(alertType);
                alert.setAlertLevel(alertLevel);
                alert.setCurrentValue(BigDecimal.valueOf(currentValue));
                alert.setThresholdMin(config.getThresholdMin());
                alert.setThresholdMax(config.getThresholdMax());
                alert.setMessage(message);
                alert.setSuggestion(suggestion);
                alert.setStatus("pending");
                alert.setCreateTime(LocalDateTime.now());
                alertMapper.insert(alert);
            } else {
                // 已存在未处理的同类预警：刷新当前值；若级别升级(如 medium→high)则同步升级，
                // 避免旧 pending 预警永久压制同类新预警、导致级别升级被丢弃。
                boolean changed = false;
                if (levelRank(alertLevel) > levelRank(existingAlert.getAlertLevel())) {
                    existingAlert.setAlertLevel(alertLevel);
                    existingAlert.setMessage(message);
                    existingAlert.setSuggestion(suggestion);
                    changed = true;
                }
                BigDecimal newVal = BigDecimal.valueOf(currentValue);
                if (existingAlert.getCurrentValue() == null
                        || existingAlert.getCurrentValue().compareTo(newVal) != 0) {
                    existingAlert.setCurrentValue(newVal);
                    changed = true;
                }
                if (changed) {
                    alertMapper.updateById(existingAlert);
                }
            }
        }
    }

    /** 预警级别排序：high > medium > low，用于判断是否需要升级 */
    private int levelRank(String level) {
        if ("high".equals(level)) return 3;
        if ("medium".equals(level)) return 2;
        if ("low".equals(level)) return 1;
        return 0;
    }

    /**
     * 生成操作建议
     */
    private String generateSuggestion(String alertType, double currentValue, double threshold, boolean isLow) {
        switch (alertType) {
            case "soil_humidity":
                if (isLow) {
                    int minutes = (int) ((threshold - currentValue) / 2 * 10); // 估算浇灌时间
                    return String.format("%s号田土壤湿度%.1f%%，建议浇灌约%d分钟", "", currentValue, Math.max(10, Math.min(minutes, 60)));
                }
                return "土壤湿度过高，建议减少浇灌或检查排水系统";
            
            case "light":
                if (isLow) {
                    return "光照不足，建议开启补光灯或检查光照设备";
                }
                return "光照过强，建议适当遮阳";
            
            case "temperature":
                if (isLow) {
                    return "温度过低，建议开启保温设备或增加覆盖";
                }
                return "温度过高，建议开启通风设备或增加遮阳";
            
            case "air_humidity":
                if (isLow) {
                    return "空气湿度过低，建议增加加湿设备";
                }
                return "空气湿度过高，建议开启通风设备";
            
            case "ph":
                if (isLow) {
                    return "pH值偏低，建议添加碱性肥料或石灰";
                }
                return "pH值偏高，建议添加酸性肥料或硫磺";
            
            case "carbon":
                if (isLow) {
                    return "CO2含量偏低，建议增加通风或CO2补充设备";
                }
                return "CO2含量过高，建议增加通风";
            
            default:
                return "请检查相关设备并采取相应措施";
        }
    }

    @Override
    public Map<String, Object> getHealthIndexConfig() {
        List<HealthIndexConfig> configs = configMapper.selectList(null);
        Map<String, Object> result = new HashMap<>();
        for (HealthIndexConfig config : configs) {
            Map<String, Object> configMap = new HashMap<>();
            configMap.put("excellentMin", config.getExcellentMin());
            configMap.put("excellentMax", config.getExcellentMax());
            configMap.put("goodMin", config.getGoodMin());
            configMap.put("goodMax", config.getGoodMax());
            configMap.put("weight", config.getWeight());
            configMap.put("thresholdMin", config.getThresholdMin());
            configMap.put("thresholdMax", config.getThresholdMax());
            result.put(config.getIndicator(), configMap);
        }
        return result;
    }
}

