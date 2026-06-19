CREATE TABLE IF NOT EXISTS `auto_patrol_log` (
  `id` int NOT NULL AUTO_INCREMENT,
  `patrol_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `trigger_type` varchar(20) NOT NULL DEFAULT 'scheduled',
  `farm_name` varchar(100) DEFAULT NULL,
  `action_type` varchar(50) DEFAULT NULL,
  `action_detail` text DEFAULT NULL,
  `reason` text DEFAULT NULL,
  `result` varchar(20) NOT NULL DEFAULT 'success',
  `ai_report` text DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_patrol_time` (`patrol_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
