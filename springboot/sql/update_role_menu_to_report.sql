-- 将"系统角色管理"菜单项更新为"数据报表中心"
-- 对应 Role.vue 已改写为数据报表中心的内容
UPDATE `sys_menu`
SET
  `name`  = '数据报表中心',
  `icon`  = 'el-icon-data-analysis',
  `remark` = '帮帮农智慧农业数据可视化报表'
WHERE `id` = 6;
