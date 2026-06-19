CREATE TABLE IF NOT EXISTS `sensor_event` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `farm_name` varchar(125) NOT NULL,
  `event_type` varchar(32) NOT NULL,
  `metric_name` varchar(32) NOT NULL,
  `current_value` decimal(10,2) NOT NULL,
  `threshold_value` decimal(10,2) NOT NULL,
  `severity` varchar(16) NOT NULL,
  `handled` tinyint DEFAULT 0,
  `chain_id` varchar(64) DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_handled` (`handled`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
