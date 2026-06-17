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
     * 土壤湿度 % (土壤传感器 AO -> PA4)
     */
    private Integer soilHumidity;

    /**
     * LED状态 (0-关闭, 1-开启)
     */
    private Integer led;

    /**
     * 风扇状态 (0-关闭, 1-开启)，设备按温度自动控制
     */
    private Integer fan;

    /**
     * 蜂鸣器状态 (0-静音, 1-报警)，设备按高温/土壤过干自动触发
     */
    private Integer buzzer;

    /**
     * 设备名称
     */
    private String deviceName;
    
    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
