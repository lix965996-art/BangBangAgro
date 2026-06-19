-- Add last login time for attendance/statistics features.
ALTER TABLE `sys_user`
  ADD COLUMN `last_login_time` datetime DEFAULT NULL COMMENT 'last login time' AFTER `status`;
