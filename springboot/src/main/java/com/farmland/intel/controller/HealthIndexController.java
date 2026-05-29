package com.farmland.intel.controller;

import com.farmland.intel.common.Result;
import com.farmland.intel.entity.Statistic;
import com.farmland.intel.mapper.StatisticMapper;
import com.farmland.intel.service.IHealthIndexService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 健康指数控制器
 */
@RestController
@RequestMapping("/health-index")
public class HealthIndexController {

    @Autowired
    private IHealthIndexService healthIndexService;

    @Autowired
    private StatisticMapper statisticMapper;

    /**
     * 计算指定农田的健康指数
     */
    @GetMapping("/calculate/{farmlandId}")
    public Result calculateHealthIndex(@PathVariable Integer farmlandId) {
        Statistic statistic = statisticMapper.selectById(farmlandId);
        if (statistic == null) {
            return Result.error("404", "农田不存在");
        }

        Integer healthIndex = healthIndexService.calculateHealthIndex(statistic);
        
        Map<String, Object> result = new HashMap<>();
        result.put("farmlandId", farmlandId);
        result.put("farmlandName", statistic.getFarm());
        result.put("healthIndex", healthIndex);
        result.put("level", getHealthLevel(healthIndex));

        return Result.success(result);
    }

    /**
     * 批量计算所有农田的健康指数
     */
    @GetMapping("/calculate/all")
    public Result calculateAllHealthIndex() {
        List<Statistic> statistics = statisticMapper.selectList(null);
        List<com.farmland.intel.entity.Statistic> list = statistics;
        
        Map<String, Object> result = new HashMap<>();
        for (Statistic statistic : list) {
            Integer healthIndex = healthIndexService.calculateHealthIndex(statistic);
            Map<String, Object> item = new HashMap<>();
            item.put("farmlandId", statistic.getId());
            item.put("farmlandName", statistic.getFarm());
            item.put("healthIndex", healthIndex);
            item.put("level", getHealthLevel(healthIndex));
            result.put(String.valueOf(statistic.getId()), item);
        }

        return Result.success(result);
    }

    /**
     * 获取健康指数配置
     */
    @GetMapping("/config")
    public Result getConfig() {
        return Result.success(healthIndexService.getHealthIndexConfig());
    }

    /**
     * 获取健康等级
     */
    private String getHealthLevel(Integer healthIndex) {
        if (healthIndex == null) return "poor";
        if (healthIndex >= 90) return "excellent";
        if (healthIndex >= 75) return "good";
        if (healthIndex >= 60) return "normal";
        return "poor";
    }
}

