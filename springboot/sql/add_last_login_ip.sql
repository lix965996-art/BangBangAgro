-- 用户表新增"最后登录 IP / 归属地"字段
-- 登录时记录客户端 IP，管理员可在用户管理页看到；归属地由高德 IP 定位大致解析(城市级，可能不准)
-- 执行一次即可；列已存在会报错，忽略即可。
ALTER TABLE `sys_user`
  ADD COLUMN `last_login_ip` VARCHAR(64) DEFAULT NULL COMMENT '最后登录IP' AFTER `last_login_time`;

ALTER TABLE `sys_user`
  ADD COLUMN `last_login_region` VARCHAR(100) DEFAULT NULL COMMENT '最后登录IP归属地(高德解析,城市级大致)' AFTER `last_login_ip`;
