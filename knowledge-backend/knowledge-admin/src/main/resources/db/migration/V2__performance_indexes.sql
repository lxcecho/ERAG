-- V2: 性能优化 - 索引补充 + ai_call_log 分区准备
-- 说明：为高频查询字段补充索引，提升多租户场景下的查询性能

-- 1. 确保所有业务表的 tenant_id 字段有索引（MyBatis-Plus 租户拦截器每条 SQL 都会加 WHERE tenant_id）
-- 以下为关键表的 tenant_id 索引（IF NOT EXISTS 语法兼容 MySQL 8.0+）

-- knowledge_base 表
CREATE INDEX IF NOT EXISTS idx_kb_tenant_id ON knowledge_base(tenant_id);
CREATE INDEX IF NOT EXISTS idx_kb_tenant_status ON knowledge_base(tenant_id, status);

-- kb_document 表
CREATE INDEX IF NOT EXISTS idx_doc_tenant_id ON kb_document(tenant_id);
CREATE INDEX IF NOT EXISTS idx_doc_tenant_kb ON kb_document(tenant_id, kb_id);
CREATE INDEX IF NOT EXISTS idx_doc_status ON kb_document(tenant_id, status);

-- chat_session 表
CREATE INDEX IF NOT EXISTS idx_session_tenant_user ON chat_session(tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_session_tenant_kb ON chat_session(tenant_id, kb_id);

-- chat_message 表
CREATE INDEX IF NOT EXISTS idx_message_session ON chat_message(session_id, create_time);

-- ai_call_log 表（高频写入，查询按时间+租户）
CREATE INDEX IF NOT EXISTS idx_calllog_tenant_time ON ai_call_log(tenant_id, create_time);
CREATE INDEX IF NOT EXISTS idx_calllog_model ON ai_call_log(tenant_id, model_name, create_time);

-- prompt_template 表
CREATE INDEX IF NOT EXISTS idx_prompt_tenant_type ON prompt_template(tenant_id, type);

-- agent_task 表
CREATE INDEX IF NOT EXISTS idx_agent_task_tenant_user ON agent_task(tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_agent_task_status ON agent_task(tenant_id, status);

-- workflow_task 表
CREATE INDEX IF NOT EXISTS idx_wf_task_tenant_user ON workflow_task(tenant_id, user_id);

-- agent_memory 表（长期记忆衰减查询）
CREATE INDEX IF NOT EXISTS idx_memory_user_type ON agent_memory(user_id, memory_type, create_time);

-- 2. 添加 refresh token 相关字段到 sys_user（可选，当前通过 Redis 管理）
-- 如需持久化 refresh token，可取消注释以下语句：
-- ALTER TABLE sys_user ADD COLUMN refresh_token VARCHAR(512) DEFAULT NULL COMMENT '当前有效 refresh token hash';
-- ALTER TABLE sys_user ADD COLUMN refresh_token_expire DATETIME DEFAULT NULL COMMENT 'refresh token 过期时间';
