package com.farmland.intel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farmland.intel.entity.FarmlandAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 农田预警Mapper
 */
@Mapper
public interface FarmlandAlertMapper extends BaseMapper<FarmlandAlert> {
    
    /**
     * 查询今日未处理的预警
     */
    @Select("SELECT * FROM farmland_alert WHERE status = 'pending' AND DATE(create_time) = CURDATE() ORDER BY create_time DESC")
    List<FarmlandAlert> selectTodayPendingAlerts();
}

