-- Add last login IP and coarse region fields.
ALTER TABLE `sys_user`
  ADD COLUMN `last_login_ip` VARCHAR(64) DEFAULT NULL COMMENT 'last login IP' AFTER `last_login_time`;

ALTER TABLE `sys_user`
  ADD COLUMN `last_login_region` VARCHAR(100) DEFAULT NULL COMMENT 'last login IP region' AFTER `last_login_ip`;
