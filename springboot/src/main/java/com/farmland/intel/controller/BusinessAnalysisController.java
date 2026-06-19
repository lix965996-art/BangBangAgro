package com.farmland.intel.controller;

import com.farmland.intel.common.Result;
import com.farmland.intel.entity.CropYieldConfig;
import com.farmland.intel.entity.Statistic;
import com.farmland.intel.service.ICropYieldConfigService;
import com.farmland.intel.service.IStatisticService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 经营分析控制器
 */
@RestController
@RequestMapping("/business-analysis")
public class BusinessAnalysisController {

    @Autowired
    private IStatisticService statisticService;

    @Autowired
    private ICropYieldConfigService cropYieldConfigService;

    /**
     * 获取所有农田的经营分析数据
     */
    @GetMapping("/all")
    public Result getAllBusinessAnalysis() {
        List<Statistic> statistics = statisticService.list();
        List<CropYieldConfig> configs = cropYieldConfigService.list();

        // 构建作物配置Map
        Map<String, CropYieldConfig> configMap = new HashMap<>();
        for (CropYieldConfig config : configs) {
            configMap.put(config.getCropName(), config);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        Map<String, Object> cropProfitMap = new HashMap<>(); // 按作物类型统计利润

        for (Statistic statistic : statistics) {
            Map<String, Object> item = calculateBusinessData(statistic, configMap);
            result.add(item);

            // 累计作物利润
            String cropName = statistic.getCrop();
            if (cropName != null) {
                BigDecimal profit = (BigDecimal) item.get("profit");
                if (profit != null) {
                    BigDecimal existingProfit = (BigDecimal) cropProfitMap.getOrDefault(cropName, BigDecimal.ZERO);
                    cropProfitMap.put(cropName, existingProfit.add(profit));
                }
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("farmlandData", result);
        response.put("cropProfit", cropProfitMap);

        return Result.success(response);
    }

    /**
     * 获取指定农田的经营分析数据
     */
    @GetMapping("/farmland/{farmlandId}")
    public Result getFarmlandBusinessAnalysis(@PathVariable Integer farmlandId) {
        Statistic statistic = statisticService.getById(farmlandId);
        if (statistic == null) {
            return Result.error("404", "农田不存在");
        }

        List<CropYieldConfig> configs = cropYieldConfigService.list();
        Map<String, CropYieldConfig> configMap = new HashMap<>();
        for (CropYieldConfig config : configs) {
            configMap.put(config.getCropName(), config);
        }

        Map<String, Object> result = calculateBusinessData(statistic, configMap);
        return Result.success(result);
    }

    /**
     * 计算单个农田的经营数据
     */
    private Map<String, Object> calculateBusinessData(Statistic statistic, Map<String, CropYieldConfig> configMap) {
        Map<String, Object> result = new HashMap<>();
        
        result.put("farmlandId", statistic.getId());
        result.put("farmlandName", statistic.getFarm());
        result.put("crop", statistic.getCrop());
        
        // 获取面积（支持小数，如 "12.5"；String 列用 Double 解析，避免整数解析把带小数面积归 0）
        double area = 0;
        if (statistic.getArea() != null) {
            try {
                area = Double.parseDouble(statistic.getArea().toString().trim());
            } catch (Exception e) {
                area = 0;
            }
        }
        result.put("area", area);

        // 获取作物配置
        CropYieldConfig config = configMap.get(statistic.getCrop());
        if (config == null) {
            // 使用默认值
            result.put("expectedYield", BigDecimal.ZERO);
            result.put("expectedRevenue", BigDecimal.ZERO);
            result.put("expectedCost", BigDecimal.ZERO);
            result.put("profit", BigDecimal.ZERO);
            return result;
        }

        // 计算预计产量（公斤）
        BigDecimal expectedYield = config.getYieldPerMu()
            .multiply(BigDecimal.valueOf(area))
            .setScale(2, RoundingMode.HALF_UP);
        result.put("expectedYield", expectedYield);

        // 计算预计收益（元）
        BigDecimal expectedRevenue = expectedYield
            .multiply(config.getUnitPrice())
            .setScale(2, RoundingMode.HALF_UP);
        result.put("expectedRevenue", expectedRevenue);

        // 计算预计成本（元）
        BigDecimal expectedCost = config.getCostPerMu()
            .multiply(BigDecimal.valueOf(area))
            .setScale(2, RoundingMode.HALF_UP);
        result.put("expectedCost", expectedCost);

        // 计算预计利润（元）
        BigDecimal expectedProfit = expectedRevenue
            .subtract(expectedCost)
            .setScale(2, RoundingMode.HALF_UP);
        result.put("profit", expectedProfit);

        return result;
    }
}

