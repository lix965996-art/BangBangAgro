package com.farmland.intel.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.entity.FarmlandAlert;
import com.farmland.intel.mapper.FarmlandAlertMapper;
import com.farmland.intel.service.IFarmlandAlertService;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 农田预警服务实现类
 */
@Service
public class FarmlandAlertServiceImpl extends ServiceImpl<FarmlandAlertMapper, FarmlandAlert>
        implements IFarmlandAlertService {

    @Override
    public List<FarmlandAlert> selectTodayPendingAlerts() {
        return baseMapper.selectTodayPendingAlerts();
    }

    @Override
    public List<Map<String, Object>> getAlertTypeStats(int limit) {
        List<FarmlandAlert> all = list();
        // 告警类型中文映射
        Map<String, String> typeLabels = new LinkedHashMap<>();
        typeLabels.put("temperature", "温度预警");
        typeLabels.put("soil_humidity", "土壤湿度");
        typeLabels.put("air_humidity", "空气湿度");
        typeLabels.put("visual", "视觉巡检");
        typeLabels.put("iot_visual", "IoT视觉");
        typeLabels.put("ph", "酸碱度");
        typeLabels.put("carbon", "二氧化碳");
        typeLabels.put("light", "光照预警");

        Map<String, Long> countMap = all.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getAlertType() != null ? a.getAlertType() : "other",
                        Collectors.counting()));

        long total = all.size();

        return countMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(limit)
                .map(e -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("alertType", e.getKey());
                    item.put("label", typeLabels.getOrDefault(e.getKey(), e.getKey()));
                    item.put("count", e.getValue());
                    item.put("percent", total > 0 ? Math.round(e.getValue() * 100.0 / total) : 0);
                    return item;
                })
                .collect(Collectors.toList());
    }
}
