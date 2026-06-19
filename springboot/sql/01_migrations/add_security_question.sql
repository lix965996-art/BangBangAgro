-- Add password recovery security question fields.
ALTER TABLE `sys_user`
  ADD COLUMN `security_question` varchar(100) DEFAULT NULL COMMENT 'security question' AFTER `last_login_time`,
  ADD COLUMN `security_answer` varchar(100) DEFAULT NULL COMMENT 'security answer' AFTER `security_question`;
