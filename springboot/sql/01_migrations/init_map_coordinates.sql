-- =====================================================
-- 真实地图3D农场 - 坐标数据初始化
-- 直接更新数据，不管字段是否存在
-- =====================================================

-- 如果字段已存在会报错，忽略即可
-- 如果字段不存在，会添加字段

ALTER TABLE `statistic` ADD COLUMN `center_lng` DECIMAL(10, 7) NULL COMMENT '中心经度' AFTER `keeper`;
ALTER TABLE `statistic` ADD COLUMN `center_lat` DECIMAL(10, 7) NULL COMMENT '中心纬度' AFTER `center_lng`;
ALTER TABLE `statistic` ADD COLUMN `coordinates` TEXT NULL COMMENT '区域坐标JSON' AFTER `center_lat`;

-- 为所有农田设置张家界地区的坐标
UPDATE `statistic` SET 
  `center_lng` = 110.476 + (id * 0.001),
  `center_lat` = 29.117 + (id * 0.001),
  `coordinates` = CONCAT(
    '[',
    '{"lng":', 110.476 + (id * 0.001), ',"lat":', 29.117 + (id * 0.001) + 0.0005, '},',
    '{"lng":', 110.476 + (id * 0.001) + 0.001, ',"lat":', 29.117 + (id * 0.001) + 0.0005, '},',
    '{"lng":', 110.476 + (id * 0.001) + 0.001, ',"lat":', 29.117 + (id * 0.001), '},',
    '{"lng":', 110.476 + (id * 0.001), ',"lat":', 29.117 + (id * 0.001), '}',
    ']'
  );

-- 查看结果
SELECT id, farm AS '农田', crop AS '作物', center_lng AS '经度', center_lat AS '纬度' FROM `statistic` ORDER BY id;
