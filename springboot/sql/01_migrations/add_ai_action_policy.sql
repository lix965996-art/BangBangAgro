-- Add per-user AI write policy and task owner fields.
ALTER TABLE sys_ai_config
    ADD COLUMN ai_action_policy VARCHAR(20) DEFAULT 'semi_approval'
    COMMENT 'AI action policy: full_auto, semi_approval, full_approval';

ALTER TABLE agent_task_queue
    ADD COLUMN user_id INT NULL COMMENT 'requesting user id';
