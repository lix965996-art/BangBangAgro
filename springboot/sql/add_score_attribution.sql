-- =====================================================================
-- 周滚动员工评分 — 为 3 张源表补充"操作人 userId"归位列
-- 类型 INT, 对齐 sys_user.id (IdType.AUTO Integer); 不加物理 FK (项目惯例)
-- 在已部署的运行库直接执行即可 (表已存在)。
-- =====================================================================

ALTER TABLE `farmland_alert`
  ADD COLUMN `processor_user_id` INT DEFAULT NULL COMMENT '处理人 sys_user.id (归因用)' AFTER `processor`;

ALTER TABLE `auto_patrol_log`
  ADD COLUMN `operator_id` INT DEFAULT NULL COMMENT '手动触发操作员 sys_user.id' AFTER `trigger_type`;

ALTER TABLE `knowledge_document`
  ADD COLUMN `created_by` INT DEFAULT NULL COMMENT '创建人 sys_user.id' AFTER `source`,
  ADD COLUMN `updated_by` INT DEFAULT NULL COMMENT '最后修改人 sys_user.id' AFTER `created_by`;

-- ---- 可选: 加速周聚合查询(数据量大时取消注释执行, 每条仅运行一次) ----
-- CREATE INDEX idx_adc_user_type_time ON agent_decision_chain (user_id, step_type, created_at);
-- CREATE INDEX idx_atq_approver_time  ON agent_task_queue (approved_by, approved_at);
-- CREATE INDEX idx_fa_proc_time       ON farmland_alert (processor_user_id, status, process_time);
-- CREATE INDEX idx_apl_operator_time  ON auto_patrol_log (operator_id, patrol_time);
-- CREATE INDEX idx_kd_creator_time    ON knowledge_document (created_by, created_at);
