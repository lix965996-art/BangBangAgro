package com.farmland.intel.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 传感器读数实体类
 */
@Data
@TableName("sensor_reading")
public class SensorReading {
    
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    /**
     * 温度
     */
    private Double temperature;
    
    /**
     * 湿度
     */
    private Double humidity;
    
    /**
     * LED状态 (0-关闭, 1-开启)
     */
    private Integer led;
    
    /**
     * 设备名称
     */
    private String deviceName;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
