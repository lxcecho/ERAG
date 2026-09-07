-- ERAG 集成测试初始化脚本
-- 仅创建测试所需的最小表结构，不包含种子数据

-- 用户表
CREATE TABLE IF NOT EXISTS sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    username VARCHAR(64) NOT NULL,
    password VARCHAR(256) NOT NULL,
    nickname VARCHAR(64),
    avatar VARCHAR(512),
    email VARCHAR(128),
    phone VARCHAR(32),
    status TINYINT DEFAULT 0 COMMENT '0-正常 1-禁用',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    UNIQUE KEY uk_tenant_username (tenant_id, username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 角色表
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    role_name VARCHAR(64) NOT NULL,
    role_key VARCHAR(64) NOT NULL,
    status TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 用户角色关联表
CREATE TABLE IF NOT EXISTS sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 知识库表
CREATE TABLE IF NOT EXISTS knowledge_base (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    status TINYINT DEFAULT 0,
    doc_count INT DEFAULT 0,
    create_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 文档表
CREATE TABLE IF NOT EXISTS kb_document (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    kb_id BIGINT NOT NULL,
    name VARCHAR(256) NOT NULL,
    type VARCHAR(32),
    size BIGINT DEFAULT 0,
    status TINYINT DEFAULT 0 COMMENT '0-待解析 1-解析中 2-已解析 3-解析失败',
    md5 VARCHAR(64),
    storage_path VARCHAR(512),
    chunk_count INT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_doc_kb (kb_id),
    INDEX idx_doc_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 聊天会话表
CREATE TABLE IF NOT EXISTS chat_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    user_id BIGINT NOT NULL,
    kb_id BIGINT,
    title VARCHAR(256),
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT DEFAULT 0,
    INDEX idx_session_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 聊天消息表
CREATE TABLE IF NOT EXISTS chat_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    role VARCHAR(32) NOT NULL,
    content TEXT,
    sources TEXT COMMENT 'JSON 格式的引用来源',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_message_session (session_id, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Agent 记忆表
CREATE TABLE IF NOT EXISTS agent_memory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    user_id BIGINT,
    session_id BIGINT,
    memory_type VARCHAR(32) NOT NULL,
    content TEXT,
    source_session_id BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_memory_user_type (user_id, memory_type, create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 操作日志表
CREATE TABLE IF NOT EXISTS sys_oper_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    title VARCHAR(128),
    business_type TINYINT DEFAULT 0,
    method VARCHAR(256),
    request_method VARCHAR(16),
    request_url VARCHAR(512),
    request_param TEXT,
    response_result TEXT,
    status TINYINT DEFAULT 0,
    error_msg TEXT,
    oper_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    user_id BIGINT,
    user_name VARCHAR(64)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
