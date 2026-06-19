CREATE TABLE IF NOT EXISTS `sys_ai_config` (
  `id` int NOT NULL AUTO_INCREMENT,
  `user_id` int NOT NULL,
  `provider` varchar(50) NOT NULL DEFAULT 'qwen',
  `base_url` varchar(512) NOT NULL,
  `api_key` varchar(512) NOT NULL DEFAULT '',
  `model_name` varchar(100) NOT NULL DEFAULT 'qwen-max',
  `chat_model_name` varchar(100) NOT NULL DEFAULT '',
  `chat_base_url` varchar(512) NOT NULL DEFAULT '',
  `chat_api_key` varchar(512) NOT NULL DEFAULT '',
  `temperature` decimal(4,2) NOT NULL DEFAULT 0.42,
  `enabled` tinyint(1) NOT NULL DEFAULT 1,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
