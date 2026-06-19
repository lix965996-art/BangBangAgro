-- Add fields uploaded by the STM32 ZET6 firmware: soil humidity, fan, and buzzer.
ALTER TABLE sensor_reading
    ADD COLUMN soil_humidity INT NULL COMMENT 'soil humidity percent' AFTER humidity;

ALTER TABLE sensor_reading
    ADD COLUMN fan TINYINT NULL COMMENT 'fan status: 0 off, 1 on' AFTER led;

ALTER TABLE sensor_reading
    ADD COLUMN buzzer TINYINT NULL COMMENT 'buzzer status: 0 silent, 1 alarm' AFTER fan;
