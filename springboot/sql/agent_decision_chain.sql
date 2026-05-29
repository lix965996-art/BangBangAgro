CREATE TABLE IF NOT EXISTS `agent_decision_chain` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `chain_id` varchar(64) NOT NULL,
  `user_id` int DEFAULT NULL,
  `trigger_source` varchar(32) NOT NULL,
  `user_question` text,
  `step_index` int NOT NULL,
  `step_type` varchar(32) NOT NULL,
  `step_content` text,
  `step_detail` json DEFAULT NULL,
  `model_name` varchar(64) DEFAULT NULL,
  `round_number` int DEFAULT NULL,
  `duration_ms` int DEFAULT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_chain_id` (`chain_id`),
  KEY `idx_trigger_source` (`trigger_source`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
