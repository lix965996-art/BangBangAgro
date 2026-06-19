-- =====================================================================
-- farmland_alert 建表参考 (仅全新环境使用)
--
-- 背景: 仓库内没有 farmland_alert 的建表 DDL (该表只存在于运行库)。
--   add_score_attribution.sql 里的 ALTER TABLE farmland_alert 在已部署库
--   可直接执行; 但若在全新空库部署, 表不存在会报错, 此时先跑本文件建表。
--   本文件依据 entity/FarmlandAlert.java 字段重建, 若你的运行库结构有出入,
--   以运行库为准, 本文件仅作参考。
-- =====================================================================

CREATE TABLE IF NOT EXISTS `farmland_alert` (
  `id`                INT          NOT NULL AUTO_INCREMENT,
  `farmland_id`       INT          DEFAULT NULL COMMENT '农田ID',
  `farmland_name`     VARCHAR(100) DEFAULT NULL COMMENT '农田名称',
  `alert_type`        VARCHAR(50)  DEFAULT NULL COMMENT 'temperature/soil_humidity/air_humidity/ph/carbon/light/visual/iot_visual',
  `alert_level`       VARCHAR(20)  DEFAULT NULL COMMENT 'low/medium/high',
  `current_value`     DECIMAL(10,2) DEFAULT NULL COMMENT '当前值',
  `threshold_min`     DECIMAL(10,2) DEFAULT NULL COMMENT '阈值下限',
  `threshold_max`     DECIMAL(10,2) DEFAULT NULL COMMENT '阈值上限',
  `message`           TEXT         DEFAULT NULL COMMENT '预警消息',
  `suggestion`        TEXT         DEFAULT NULL COMMENT '操作建议',
  `status`            VARCHAR(20)  NOT NULL DEFAULT 'pending' COMMENT 'pending/processed',
  `create_time`       DATETIME     DEFAULT NULL COMMENT '创建时间',
  `process_time`      DATETIME     DEFAULT NULL COMMENT '处理时间',
  `processor`         VARCHAR(100) DEFAULT NULL COMMENT '处理人(自由文本, 保留向后兼容)',
  `processor_user_id` INT          DEFAULT NULL COMMENT '处理人 sys_user.id (归因用)',
  PRIMARY KEY (`id`),
  KEY `idx_status_create` (`status`, `create_time`),
  KEY `idx_farmland` (`farmland_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='农田预警';
