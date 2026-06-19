-- =============================================================================
-- 清理被 fallbackPlan 污染的历史 AI 巡检报告
-- =============================================================================
--
-- 背景:
--   v1.1.0 之前的 runAiAnalysis 在 LLM 不可用时会走 fallbackPlan(1000 行 if-else),
--   把"巡检请求 prompt"误判为"对话场景",返回 "为您打开XX界面" 之类的对话话术,
--   写入了 auto_patrol_log.ai_report 字段。
--
-- 修复方式:
--   代码层已在 AgentService.buildPlanWithTriggerSource 修复(triggerSource=auto_patrol
--   时屏蔽 fallback)。本脚本清理历史数据。
--
-- 使用方式:
--   方式 1 (推荐): 应用启动后,管理员调用 POST /api/patrol/cleanup-ai-reports
--   方式 2 (本脚本): 不重启应用直接在 MySQL 客户端执行,适合应急
--
-- 安全性:
--   - 只 UPDATE,不 DELETE,日志条目本身保留
--   - 只清理 action_type='ai_analysis' 的字段
--   - 只把字段置 NULL,不影响其他列
--   - 先 SELECT 看影响行数,确认后再 UPDATE
-- =============================================================================

-- 第 1 步:先看会影响多少条
SELECT COUNT(*) AS '即将清理的污染报告数'
FROM auto_patrol_log
WHERE action_type = 'ai_analysis'
  AND ai_report IS NOT NULL
  AND (
    ai_report LIKE '%为您打开%' OR
    ai_report LIKE '%已为您%' OR
    ai_report LIKE '%为您切换%' OR
    ai_report LIKE '%数据监控界面%' OR
    ai_report LIKE '%我是「禾序」%' OR
    ai_report LIKE '%您可以前往%' OR
    ai_report LIKE '%好的，为您%' OR
    ai_report LIKE '%好的,为您%'
  );

-- 第 2 步:确认后执行清理(把 ai_report 字段置 NULL,保留日志条目本身)
UPDATE auto_patrol_log
SET ai_report = NULL
WHERE action_type = 'ai_analysis'
  AND ai_report IS NOT NULL
  AND (
    ai_report LIKE '%为您打开%' OR
    ai_report LIKE '%已为您%' OR
    ai_report LIKE '%为您切换%' OR
    ai_report LIKE '%数据监控界面%' OR
    ai_report LIKE '%我是「禾序」%' OR
    ai_report LIKE '%您可以前往%' OR
    ai_report LIKE '%好的，为您%' OR
    ai_report LIKE '%好的,为您%'
  );

-- 第 3 步:验证清理结果
SELECT COUNT(*) AS '清理后剩余的污染报告数'
FROM auto_patrol_log
WHERE action_type = 'ai_analysis'
  AND ai_report IS NOT NULL
  AND (
    ai_report LIKE '%为您打开%' OR
    ai_report LIKE '%已为您%' OR
    ai_report LIKE '%为您切换%' OR
    ai_report LIKE '%数据监控界面%'
  );
