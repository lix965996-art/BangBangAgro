ALTER TABLE `sys_ai_config`
  ADD COLUMN `chat_model_name` varchar(100) NOT NULL DEFAULT '' AFTER `model_name`,
  ADD COLUMN `chat_base_url` varchar(512) NOT NULL DEFAULT '' AFTER `chat_model_name`,
  ADD COLUMN `chat_api_key` varchar(512) NOT NULL DEFAULT '' AFTER `chat_base_url`;
