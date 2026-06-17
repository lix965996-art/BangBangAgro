package com.farmland.intel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.farmland.intel.entity.FarmlandAlert;

import java.util.List;
import java.util.Map;

/**
 * 农田预警服务接口
 */
public interface IFarmlandAlertService extends IService<FarmlandAlert> {

    /**
     * 查询今日未处理的预警
     */
    List<FarmlandAlert> selectTodayPendingAlerts();

    /**
     * 获取告警类型统计（词云数据）
     * @return [{alertType, count}]
     */
    List<Map<String, Object>> getAlertTypeStats(int limit);
}
