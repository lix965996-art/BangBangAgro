-- ============================================================
-- 给 sys_ai_config 增加「视觉(多模态)模型」独立配置字段
-- 用于「果蔬检测 → 豆包多模态」引擎，每个用户可单独配置视觉模型 Key/URL/模型名
-- 留空则回退到后端 application.yml 的 vision.* 默认配置
-- 执行一次即可（MySQL 8.0 不支持 ADD COLUMN IF NOT EXISTS，重复执行会报 Duplicate column，忽略即可）
-- ============================================================

ALTER TABLE `sys_ai_config`
    ADD COLUMN `vision_model_name` VARCHAR(120) NULL COMMENT '视觉模型名称(留空回退后端vision.*)' AFTER `chat_api_key`,
    ADD COLUMN `vision_base_url`   VARCHAR(255) NULL COMMENT '视觉模型BaseURL'                    AFTER `vision_model_name`,
    ADD COLUMN `vision_api_key`    VARCHAR(255) NULL COMMENT '视觉模型APIKey(加密存储)'           AFTER `vision_base_url`;
