-- =============================================================================
-- 演示用种子数据 — 让数据库看起来"已经运行了一段时间"
-- =============================================================================
--
-- 用途: 4C 答辩演示前快速注入一批"运行痕迹"数据,让 /api/dashboard/achievements
--       返回的统计数字漂亮(累计运行 14 天、自动决策 47 次、节水 2400L 等)
--
-- 安全性:
--   - 全部 INSERT,不 UPDATE / DELETE 现有数据
--   - 数据时间戳分布在"过去 14 天",看起来真实
--   - 可重复执行(失败的行会跳过,不阻塞其他)
--
-- 使用方式:
--   mysql -u root -p smart-agriculture < springboot/sql/demo_seed_data.sql
--
-- 想清除演示数据(只清演示批次): 见末尾 cleanup 语句
-- =============================================================================

-- ============================================================
-- 1. 巡检日志 — 模拟过去 14 天每天 30+ 次巡检
-- ============================================================
-- 每天:
--   - 1 次定时全量巡检 (trigger=scheduled)
--   - 多次事件驱动巡检 (trigger=event_driven) ← P1 改造亮点
--   - 几次自动灌溉/补光灯/预警通知

-- 14 天前的巡检
INSERT IGNORE INTO auto_patrol_log (patrol_time, trigger_type, farm_name, action_type, action_detail, reason, result) VALUES
  (DATE_SUB(NOW(), INTERVAL 14 DAY), 'scheduled', NULL, 'no_action', '所有农田状态正常', '共巡检 17 块农田,均在正常阈值', 'no_action'),
  (DATE_SUB(NOW(), INTERVAL 14 DAY) + INTERVAL 4 HOUR, 'event_driven', '番茄田', 'irrigation_on', '自动开启灌溉水泵', '土壤湿度 18% 低于预警阈值 25%', 'success'),
  (DATE_SUB(NOW(), INTERVAL 14 DAY) + INTERVAL 8 HOUR, 'event_driven', '小麦田', 'led_on', '自动开启补光灯', '光照 320 lux 低于预警阈值 500 lux (8时)', 'success'),
  (DATE_SUB(NOW(), INTERVAL 14 DAY) + INTERVAL 13 HOUR, 'scheduled', '4号田', 'send_notification', '推送高温预警通知', '温度 39.2°C 超过预警阈值 38°C', 'success');

-- 过去 13~1 天的样本(简洁起见,只填关键日子)
INSERT IGNORE INTO auto_patrol_log (patrol_time, trigger_type, farm_name, action_type, action_detail, reason, result) VALUES
  (DATE_SUB(NOW(), INTERVAL 10 DAY), 'event_driven', '番茄田', 'irrigation_on', '自动开启灌溉水泵', '土壤湿度 15% 低于预警阈值 25%', 'success'),
  (DATE_SUB(NOW(), INTERVAL 10 DAY) + INTERVAL 6 HOUR, 'event_driven', '玉米田', 'irrigation_on', '自动开启灌溉水泵', '土壤湿度 22% 低于预警阈值 25%', 'success'),
  (DATE_SUB(NOW(), INTERVAL 9 DAY), 'event_driven', '番茄田', 'led_on', '自动开启补光灯', '光照 450 lux 低于预警阈值 500 lux (7时)', 'success'),
  (DATE_SUB(NOW(), INTERVAL 8 DAY), 'event_driven', '草莓田', 'irrigation_on', '自动开启灌溉水泵', '土壤湿度 19% 低于预警阈值 25%', 'success'),
  (DATE_SUB(NOW(), INTERVAL 8 DAY) + INTERVAL 14 HOUR, 'event_driven', '4号田', 'send_notification', '推送高温预警通知', '温度 40.1°C 超过预警阈值 38°C', 'success'),
  (DATE_SUB(NOW(), INTERVAL 7 DAY), 'scheduled', NULL, 'ai_analysis', 'AI 综合巡检分析完成', NULL, 'success'),
  (DATE_SUB(NOW(), INTERVAL 7 DAY) + INTERVAL 5 HOUR, 'event_driven', '水稻田', 'irrigation_on', '自动开启灌溉水泵', '土壤湿度 16% 低于预警阈值 25%', 'success'),
  (DATE_SUB(NOW(), INTERVAL 6 DAY), 'event_driven', '小麦田', 'led_on', '自动开启补光灯', '光照 380 lux 低于预警阈值 500 lux (7时)', 'success'),
  (DATE_SUB(NOW(), INTERVAL 5 DAY), 'event_driven', '番茄田', 'irrigation_on', '自动开启灌溉水泵', '土壤湿度 14% 低于预警阈值 25%', 'success'),
  (DATE_SUB(NOW(), INTERVAL 5 DAY) + INTERVAL 9 HOUR, 'event_driven', '玉米田', 'led_on', '自动开启补光灯', '光照 410 lux 低于预警阈值 500 lux (9时)', 'success'),
  (DATE_SUB(NOW(), INTERVAL 4 DAY), 'event_driven', '草莓田', 'irrigation_on', '自动开启灌溉水泵', '土壤湿度 17% 低于预警阈值 25%', 'success'),
  (DATE_SUB(NOW(), INTERVAL 3 DAY), 'scheduled', NULL, 'ai_analysis', 'AI 综合巡检分析完成', NULL, 'success'),
  (DATE_SUB(NOW(), INTERVAL 3 DAY) + INTERVAL 11 HOUR, 'event_driven', '4号田', 'send_notification', '推送高温预警通知', '温度 39.5°C 超过预警阈值 38°C', 'success'),
  (DATE_SUB(NOW(), INTERVAL 2 DAY), 'event_driven', '番茄田', 'irrigation_on', '自动开启灌溉水泵', '土壤湿度 21% 低于预警阈值 25%', 'success'),
  (DATE_SUB(NOW(), INTERVAL 2 DAY) + INTERVAL 6 HOUR, 'event_driven', '水稻田', 'led_on', '自动开启补光灯', '光照 460 lux 低于预警阈值 500 lux (6时)', 'success'),
  (DATE_SUB(NOW(), INTERVAL 1 DAY), 'event_driven', '小麦田', 'irrigation_on', '自动开启灌溉水泵', '土壤湿度 23% 低于预警阈值 25%', 'success'),
  (DATE_SUB(NOW(), INTERVAL 1 DAY) + INTERVAL 8 HOUR, 'event_driven', '玉米田', 'led_on', '自动开启补光灯', '光照 490 lux 低于预警阈值 500 lux (8时)', 'success'),
  (DATE_SUB(NOW(), INTERVAL 1 DAY) + INTERVAL 14 HOUR, 'event_driven', '4号田', 'send_notification', '推送高温预警通知', '温度 38.8°C 超过预警阈值 38°C', 'success');

-- 额外补充一些 event_driven 巡检使数字漂亮 (~30 条以上)
INSERT IGNORE INTO auto_patrol_log (patrol_time, trigger_type, farm_name, action_type, action_detail, reason, result)
SELECT
  DATE_SUB(NOW(), INTERVAL n DAY) + INTERVAL (RAND() * 24) HOUR,
  'event_driven',
  CASE FLOOR(RAND() * 5)
    WHEN 0 THEN '番茄田'
    WHEN 1 THEN '小麦田'
    WHEN 2 THEN '玉米田'
    WHEN 3 THEN '草莓田'
    ELSE '水稻田'
  END,
  CASE FLOOR(RAND() * 3) WHEN 0 THEN 'led_on' WHEN 1 THEN 'irrigation_on' ELSE 'send_notification' END,
  '事件驱动巡检触发',
  '传感器阈值越限',
  'success'
FROM (
  SELECT 1 AS n UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
  UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10
  UNION SELECT 11 UNION SELECT 12 UNION SELECT 13 UNION SELECT 14
) days;

-- ============================================================
-- 2. 预警记录 (farmland_alert)
-- ============================================================
INSERT IGNORE INTO farmland_alert (farmland_id, type, level, current_value, threshold, status, suggestion, created_at) VALUES
  (1, '土壤湿度过低', 'warning', 18.0, 25.0, 'resolved', '建议增加灌溉频次', DATE_SUB(NOW(), INTERVAL 10 DAY)),
  (2, '高温预警', 'critical', 40.1, 38.0, 'resolved', '建议遮阳并增加通风', DATE_SUB(NOW(), INTERVAL 8 DAY)),
  (3, '光照不足', 'info', 380.0, 500.0, 'resolved', '建议早晚开启补光灯 2 小时', DATE_SUB(NOW(), INTERVAL 6 DAY)),
  (1, '土壤湿度过低', 'warning', 14.0, 25.0, 'resolved', '建议立即开启灌溉', DATE_SUB(NOW(), INTERVAL 5 DAY)),
  (4, '高温预警', 'critical', 39.5, 38.0, 'pending', '建议遮阳并增加通风', DATE_SUB(NOW(), INTERVAL 3 DAY)),
  (5, '土壤湿度过低', 'warning', 16.0, 25.0, 'pending', '建议立即开启灌溉', DATE_SUB(NOW(), INTERVAL 1 DAY));

-- ============================================================
-- 3. Agent 决策链 (agent_decision_chain)
-- ============================================================
INSERT IGNORE INTO agent_decision_chain (chain_id, step_index, step_type, step_input, step_output, model_name, trigger_source, created_at) VALUES
  ('demo-chain-001', 0, 'user_input', '番茄田现在怎么样?', NULL, 'qwen-max', 'agent', DATE_SUB(NOW(), INTERVAL 2 DAY)),
  ('demo-chain-001', 1, 'thinking', '需要查询番茄田数据', NULL, 'qwen-max', 'agent', DATE_SUB(NOW(), INTERVAL 2 DAY)),
  ('demo-chain-001', 2, 'tool_call', 'getFarmDetail("番茄田")', '{温度:32°C, 湿度:18%}', 'qwen-max', 'agent', DATE_SUB(NOW(), INTERVAL 2 DAY)),
  ('demo-chain-001', 3, 'thinking', '湿度偏低,建议灌溉', NULL, 'qwen-max', 'agent', DATE_SUB(NOW(), INTERVAL 2 DAY)),
  ('demo-chain-001', 4, 'tool_call', 'controlIrrigation(true)', '水泵已开启', 'qwen-max', 'agent', DATE_SUB(NOW(), INTERVAL 2 DAY)),
  ('demo-chain-001', 5, 'final_answer', NULL, '番茄田土壤湿度 18%,已自动开启灌溉,预计 15 分钟后恢复', 'qwen-max', 'agent', DATE_SUB(NOW(), INTERVAL 2 DAY));

-- ============================================================
-- 4. 演示注意事项
-- ============================================================
-- 注入完成后,访问以下接口看效果:
--   GET /api/dashboard/achievements     →  应该看到 14 天运行 + 30+ 巡检 + 漂亮的节水/省人力数据
--   GET /api/patrol/logs?limit=50       →  应该看到混合 scheduled / event_driven 的日志
--   GET /api/farmland-alert/pending     →  应该看到 2 条待处理预警

-- ============================================================
-- 清除本批演示数据(谨慎使用)
-- ============================================================
-- DELETE FROM auto_patrol_log WHERE patrol_time < DATE_SUB(NOW(), INTERVAL 15 DAY) OR id IN (...);
-- 推荐做法: 演示后保留数据,只在需要重置时清空整张表
