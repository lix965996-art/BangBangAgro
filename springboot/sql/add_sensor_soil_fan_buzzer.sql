-- ============================================================
-- 为 sensor_reading 增加 土壤湿度 / 风扇 / 蜂鸣器 三个字段
-- 配套硬件：DHT11 + 土壤传感器(PA4) + 风扇(PE5) + 低电平报警器(PE6)
-- 固件上报字段：temp / humi / soil / led / fan / buzzer
-- 执行一次即可；若列已存在会报错，忽略即可。
-- ============================================================

ALTER TABLE sensor_reading
    ADD COLUMN soil_humidity INT NULL COMMENT '土壤湿度%' AFTER humidity;

ALTER TABLE sensor_reading
    ADD COLUMN fan TINYINT NULL COMMENT '风扇状态 0关 1开（设备按温度自动控制）' AFTER led;

ALTER TABLE sensor_reading
    ADD COLUMN buzzer TINYINT NULL COMMENT '蜂鸣器状态 0静音 1报警（设备按高温/土壤过干自动触发）' AFTER fan;
