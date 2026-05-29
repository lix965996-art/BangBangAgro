package com.farmland.intel.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.farmland.intel.entity.SensorReading;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 传感器读数Mapper
 */
@Mapper
public interface SensorReadingMapper extends BaseMapper<SensorReading> {
    
    /**
     * 获取指定时间范围内的数据（只获取旧设备STM32-001的数据）
     */
    @Select("SELECT * FROM sensor_reading WHERE created_at >= #{startTime} AND created_at <= #{endTime} AND (device_name = 'STM32-001' OR device_name IS NULL) ORDER BY created_at DESC LIMIT 5000")
    List<SensorReading> selectByTimeRange(@org.apache.ibatis.annotations.Param("startTime") LocalDateTime startTime, @org.apache.ibatis.annotations.Param("endTime") LocalDateTime endTime);
    
    /**
     * 获取最新的一条数据
     */
    @Select("SELECT * FROM sensor_reading ORDER BY created_at DESC LIMIT 1")
    SensorReading selectLatest();

    @Delete("DELETE FROM sensor_reading WHERE created_at < #{cutoff}")
    int deleteByTimeBefore(@org.apache.ibatis.annotations.Param("cutoff") LocalDateTime cutoff);
}
