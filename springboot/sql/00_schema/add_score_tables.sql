-- =====================================================================
-- 周滚动员工评分 — 评分快照 + 调整审计表
-- 运行一次即可 (CREATE TABLE IF NOT EXISTS 幂等)
-- =====================================================================

CREATE TABLE IF NOT EXISTS `user_score_snapshot` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT,
  `user_id`        INT          NOT NULL COMMENT 'FK sys_user.id',
  `window_start`   DATETIME     NOT NULL COMMENT '窗口起点(含) = now - 7d',
  `window_end`     DATETIME     NOT NULL COMMENT '窗口终点(不含) = now',
  `attendance_sub` DECIMAL(6,2) DEFAULT NULL COMMENT '出勤子分 0-100, NULL=该维度本周无数据',
  `alert_sub`      DECIMAL(6,2) DEFAULT NULL COMMENT '预警响应子分',
  `ai_sub`         DECIMAL(6,2) DEFAULT NULL COMMENT '智能体作业子分',
  `approval_sub`   DECIMAL(6,2) DEFAULT NULL COMMENT '审批把关子分',
  `knowledge_sub`  DECIMAL(6,2) DEFAULT NULL COMMENT '知识沉淀子分',
  `total`          DECIMAL(6,2) NOT NULL COMMENT '加权总分 0-100',
  `grade`          CHAR(1)      NOT NULL COMMENT '评级 S/A/B/C/D',
  `commentary`     TEXT         DEFAULT NULL COMMENT 'AI 综合评语',
  `data_thin`      TINYINT      NOT NULL DEFAULT 0 COMMENT '1=本周作业事件过少, 评级仅供参考',
  `is_override`    TINYINT      NOT NULL DEFAULT 0 COMMENT '1=管理员已覆写总分',
  `computed_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_window` (`user_id`, `window_end`),
  KEY `idx_window_end`  (`window_end`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='周滚动员工评分快照(每窗口追加, 保留历史)';

CREATE TABLE IF NOT EXISTS `score_adjustment` (
  `id`           BIGINT        NOT NULL AUTO_INCREMENT,
  `user_id`      INT           NOT NULL,
  `window_start` DATETIME      NOT NULL,
  `admin_id`     INT           NOT NULL COMMENT '操作管理员 sys_user.id',
  `old_total`    DECIMAL(6,2)  NOT NULL,
  `new_total`    DECIMAL(6,2)  NOT NULL,
  `reason`       VARCHAR(500)  NOT NULL,
  `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_window` (`user_id`, `window_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评分人工覆写审计';
