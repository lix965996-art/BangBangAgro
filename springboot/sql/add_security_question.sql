-- 用户表新增"安全问题"和"安全问题答案"字段，用于密码找回
ALTER TABLE `sys_user`
  ADD COLUMN `security_question` varchar(100) DEFAULT NULL COMMENT '安全问题' AFTER `last_login_time`,
  ADD COLUMN `security_answer` varchar(100) DEFAULT NULL COMMENT '安全问题答案' AFTER `security_question`;
