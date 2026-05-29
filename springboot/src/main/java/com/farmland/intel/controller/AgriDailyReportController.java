package com.farmland.intel.controller;

import com.farmland.intel.common.Constants;
import com.farmland.intel.common.Result;
import com.farmland.intel.service.IAgriDailyReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 智能体农情日报控制器
 */
@RestController
@RequestMapping("/agri-report")
public class AgriDailyReportController {

    private static final Logger log = LoggerFactory.getLogger(AgriDailyReportController.class);
    
    @Autowired
    private IAgriDailyReportService agriDailyReportService;
    
    /**
     * 获取智能农情日报
     * @param userId 用户ID（可选）
     * @param farmlandId 农田ID（可选）
     * @return 智能生成的农情日报
     */
    @GetMapping("/daily")
    public Result getDailyReport(@RequestParam(required = false) Integer userId,
                                  @RequestParam(required = false) Integer farmlandId) {
        try {
            Map<String, Object> report = agriDailyReportService.generateDailyReport(userId, farmlandId);
            return Result.success(report);
        } catch (Exception e) {
            log.error("生成农情日报失败", e);
            return Result.error(Constants.CODE_500, "生成农情日报失败，请稍后重试");
        }
    }
    
    /**
     * 获取天气分析
     */
    @GetMapping("/weather-analysis")
    public Result getWeatherAnalysis() {
        try {
            Map<String, Object> analysis = agriDailyReportService.getWeatherAnalysis();
            return Result.success(analysis);
        } catch (Exception e) {
            log.error("获取天气分析失败", e);
            return Result.error(Constants.CODE_500, "获取天气分析失败，请稍后重试");
        }
    }
    
    /**
     * 获取农田建议
     * @param farmlandId 农田ID
     */
    @GetMapping("/farmland-advice/{farmlandId}")
    public Result getFarmlandAdvice(@PathVariable Integer farmlandId) {
        try {
            String advice = agriDailyReportService.generateFarmlandAdvice(farmlandId);
            return Result.success(advice);
        } catch (Exception e) {
            log.error("获取农田建议失败, farmlandId={}", farmlandId, e);
            return Result.error(Constants.CODE_500, "获取农田建议失败，请稍后重试");
        }
    }
}
