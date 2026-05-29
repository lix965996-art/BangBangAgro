package com.farmland.intel.service;

import com.farmland.intel.entity.Statistic;

import java.util.Map;

/**
 * 健康指数服务接口
 */
public interface IHealthIndexService {
    
    /**
     * 计算农田健康指数（0-100）
     * @param statistic 农田统计数据
     * @return 健康指数
     */
    Integer calculateHealthIndex(Statistic statistic);
    
    /**
     * 检查并生成预警
     * @param statistic 农田统计数据
     */
    void checkAndGenerateAlerts(Statistic statistic);
    
    /**
     * 获取健康指数配置
     * @return 配置Map，key为指标名称，value为配置对象
     */
    Map<String, Object> getHealthIndexConfig();
}

