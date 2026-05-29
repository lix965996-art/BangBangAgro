package com.farmland.intel.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.farmland.intel.entity.AutoPatrolLog;

import java.util.List;

public interface IAutoPatrolLogService extends IService<AutoPatrolLog> {

    /** 查询最近 limit 条日志，按时间倒序 */
    List<AutoPatrolLog> getRecentLogs(int limit);
}
