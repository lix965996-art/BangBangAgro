-- 禾序 Agent 用户级长期记忆（偏好 + 滚动对话摘要）
-- 在目标库执行一次即可；若已存在表可跳过。

CREATE TABLE IF NOT EXISTS agent_user_memory (
  id BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id INT NOT NULL COMMENT 'sys_user.id',
  preferences TEXT COMMENT '用户偏好或显式「记住」的要点',
  conversation_summary TEXT COMMENT '近期多轮对话滚动摘要',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_agent_user_memory_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent 用户记忆';
