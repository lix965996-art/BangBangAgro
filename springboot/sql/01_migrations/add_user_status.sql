-- Add user status: 0 enabled, 1 disabled.
ALTER TABLE `sys_user`
  ADD COLUMN `status` int NOT NULL DEFAULT 0;
