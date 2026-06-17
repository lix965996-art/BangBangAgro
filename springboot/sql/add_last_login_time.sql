-- 用户表新增"最后登录时间"字段，用于统计今日出勤率
ALTER TABLE `sys_user`
  ADD COLUMN `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间' AFTER `status`;
