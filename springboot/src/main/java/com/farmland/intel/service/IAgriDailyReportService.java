package com.farmland.intel.service;

import java.util.Map;

/**
 * 智能体农情日报服务接口
 */
public interface IAgriDailyReportService {
    
    /**
     * 生成每日农情日报
     * @param userId 用户ID
     * @param farmlandId 农田ID
     * @return 包含日报内容的Map
     */
    Map<String, Object> generateDailyReport(Integer userId, Integer farmlandId);
    
    /**
     * 获取天气分析
     * @return 天气分析数据
     */
    Map<String, Object> getWeatherAnalysis();
    
    /**
     * 生成农田建议
     * @param farmlandId 农田ID
     * @return 农田建议文本
     */
    String generateFarmlandAdvice(Integer farmlandId);
}
