package com.farmland.intel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.farmland.intel.entity.AutoPatrolLog;
import com.farmland.intel.mapper.AutoPatrolLogMapper;
import com.farmland.intel.service.IAutoPatrolLogService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AutoPatrolLogServiceImpl extends ServiceImpl<AutoPatrolLogMapper, AutoPatrolLog>
        implements IAutoPatrolLogService {

    @Override
    public List<AutoPatrolLog> getRecentLogs(int limit) {
        return list(new QueryWrapper<AutoPatrolLog>()
                .orderByDesc("patrol_time")
                .last("LIMIT " + Math.min(limit, 200)));
    }
}
