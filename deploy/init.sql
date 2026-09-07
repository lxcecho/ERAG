-- ============================================================
-- 企业级 AI 知识库助手 - 全量建库脚本（Docker 初始化用）
-- 挂载到 MySQL 容器 /docker-entrypoint-initdb.d/ 自动执行
-- 包含：认证权限(RBAC) + 多租户 + 知识库 + 聊天记录 + Prompt模板 + 操作日志
-- ============================================================
-- 关键：显式声明客户端连接字符集为 utf8mb4，否则 MySQL 默认 latin1 会导致
-- 中文 COMMENT/INSERT 数据被双重编码（前端表现为"锟斤拷"/乱码）。
-- docker-entrypoint-initdb.d 以 --default-character-set 加载本文件时仍受
-- connection charset 影响，SET NAMES 是最稳的兜底。
SET NAMES utf8mb4;
CREATE DATABASE IF NOT EXISTS knowledge_ai DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE knowledge_ai;

-- ============================================================
-- 零、多租户核心表（平台级，不受 tenant_id 隔离）
-- ============================================================
DROP TABLE IF EXISTS sys_tenant;
CREATE TABLE sys_tenant (
  id              BIGINT       NOT NULL                COMMENT '租户ID(主键)，0=平台保留',
  tenant_code     VARCHAR(64)  NOT NULL                COMMENT '租户编码(唯一，登录@code可用)',
  tenant_name     VARCHAR(128) NOT NULL                COMMENT '租户名称(企业名)',
  contact_name    VARCHAR(64)  DEFAULT ''              COMMENT '联系人',
  contact_phone   VARCHAR(20)  DEFAULT ''              COMMENT '联系电话',
  status          TINYINT      DEFAULT 0               COMMENT '状态 0正常 1停用',
  max_storage_mb  BIGINT       DEFAULT 10240           COMMENT '存储上限(MB)',
  max_users       INT          DEFAULT 50              COMMENT '最大用户数',
  expire_time     DATETIME     DEFAULT NULL            COMMENT '过期时间(NULL=永久)',
  deleted         TINYINT      DEFAULT 0               COMMENT '删除标志 0存在 1删除',
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_code (tenant_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户表(平台级全局)';

DROP TABLE IF EXISTS sys_tenant_menu;
CREATE TABLE sys_tenant_menu (
  tenant_id BIGINT NOT NULL COMMENT '租户ID',
  menu_id   BIGINT NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (tenant_id, menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '租户授权菜单(平台给租户开哪些功能)';

-- ============================================================
-- 一、认证权限模块（RBAC 五表，全部受 tenant_id 隔离）
-- ============================================================
DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
  id          BIGINT       NOT NULL                COMMENT '主键',
  tenant_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID(0=平台)',
  username    VARCHAR(64)  NOT NULL                COMMENT '用户名',
  password    VARCHAR(128) NOT NULL                COMMENT '密码（BCrypt）',
  nickname    VARCHAR(64)  DEFAULT ''              COMMENT '昵称',
  avatar      VARCHAR(255) DEFAULT ''              COMMENT '头像',
  email       VARCHAR(128) DEFAULT ''              COMMENT '邮箱',
  phone       VARCHAR(20)  DEFAULT ''              COMMENT '手机',
  status      TINYINT      DEFAULT 0               COMMENT '状态 0正常 1停用',
  deleted     TINYINT      DEFAULT 0               COMMENT '删除标志 0存在 1删除',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_username (tenant_id, username),
  KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户表(租户内唯一)';

DROP TABLE IF EXISTS sys_role;
CREATE TABLE sys_role (
  id          BIGINT       NOT NULL                COMMENT '主键',
  tenant_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID(0=平台全局角色)',
  role_name   VARCHAR(64)  NOT NULL                COMMENT '角色名称',
  role_key    VARCHAR(64)  NOT NULL                COMMENT '角色权限字符串',
  role_scope  CHAR(1)      DEFAULT 'T'             COMMENT 'P=平台级/T=租户级',
  status      TINYINT      DEFAULT 0               COMMENT '状态 0正常 1停用',
  deleted     TINYINT      DEFAULT 0               COMMENT '删除标志',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_role_key (tenant_id, role_key),
  KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色表';

DROP TABLE IF EXISTS sys_menu;
CREATE TABLE sys_menu (
  id          BIGINT       NOT NULL                COMMENT '主键',
  parent_id   BIGINT       DEFAULT 0               COMMENT '父菜单ID',
  menu_name   VARCHAR(64)  NOT NULL                COMMENT '菜单名称',
  menu_type   CHAR(1)      DEFAULT 'C'             COMMENT '类型 M目录 C菜单 F按钮',
  perms       VARCHAR(128) DEFAULT ''              COMMENT '权限标识',
  path        VARCHAR(128) DEFAULT ''              COMMENT '路由路径',
  component   VARCHAR(128) DEFAULT ''              COMMENT '组件路径',
  icon        VARCHAR(64)  DEFAULT ''              COMMENT '图标',
  sort_order  INT          DEFAULT 0               COMMENT '排序',
  visible     TINYINT      DEFAULT 0               COMMENT '是否可见 0可见 1隐藏',
  status      TINYINT      DEFAULT 0               COMMENT '状态 0正常 1停用',
  deleted     TINYINT      DEFAULT 0               COMMENT '删除标志',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '菜单权限表(平台级全局，不按租户隔离)';

DROP TABLE IF EXISTS sys_user_role;
CREATE TABLE sys_user_role (
  tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '所属租户ID',
  user_id   BIGINT NOT NULL COMMENT '用户ID',
  role_id   BIGINT NOT NULL COMMENT '角色ID',
  PRIMARY KEY (tenant_id, user_id, role_id),
  KEY idx_tenant_role (tenant_id, role_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '用户角色关系';

DROP TABLE IF EXISTS sys_role_menu;
CREATE TABLE sys_role_menu (
  tenant_id BIGINT NOT NULL DEFAULT 0 COMMENT '所属租户ID',
  role_id   BIGINT NOT NULL COMMENT '角色ID',
  menu_id   BIGINT NOT NULL COMMENT '菜单ID',
  PRIMARY KEY (tenant_id, role_id, menu_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '角色菜单关系';

-- ============================================================
-- 二、知识库管理模块（全部 tenant_id 隔离）
-- ============================================================
CREATE TABLE IF NOT EXISTS knowledge_base (
  id          BIGINT       NOT NULL                COMMENT '主键',
  tenant_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID',
  name        VARCHAR(128) NOT NULL                COMMENT '知识库名称',
  description VARCHAR(512) DEFAULT ''              COMMENT '描述',
  owner_id    BIGINT       NOT NULL                COMMENT '创建人ID',
  doc_count   INT          DEFAULT 0               COMMENT '文档数量',
  status      TINYINT      DEFAULT 0               COMMENT '状态 0正常 1停用',
  deleted     TINYINT      DEFAULT 0               COMMENT '删除标志 0存在 1删除',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_owner (owner_id),
  KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识库信息';

CREATE TABLE IF NOT EXISTS kb_document (
  id            BIGINT       NOT NULL              COMMENT '主键',
  tenant_id     BIGINT       NOT NULL DEFAULT 0    COMMENT '所属租户ID',
  kb_id         BIGINT       NOT NULL              COMMENT '所属知识库ID',
  original_name VARCHAR(255) NOT NULL              COMMENT '原始文件名',
  stored_name   VARCHAR(255) NOT NULL              COMMENT '存储文件名',
  file_path     VARCHAR(512) NOT NULL              COMMENT '存储相对路径',
  file_size     BIGINT       DEFAULT 0             COMMENT '文件大小(字节)',
  file_type     VARCHAR(16)  NOT NULL              COMMENT '文件类型 pdf/doc/docx/md',
  file_suffix   VARCHAR(16)  NOT NULL              COMMENT '文件后缀(小写无点)',
  md5           VARCHAR(64)  DEFAULT ''            COMMENT 'MD5校验值',
  status        TINYINT      DEFAULT 0             COMMENT '解析状态 0待解析 1解析中 2已解析 3解析失败',
  chunk_count   INT          DEFAULT 0             COMMENT '切片数量',
  es_indexed    TINYINT      DEFAULT 0             COMMENT 'ES索引状态 0未索引 1已索引 2失败',
  -- ===== 知识治理扩展列（2026-08-01 知识治理能力） =====
  version       INT          DEFAULT 1             COMMENT '当前版本号(同文档多版本递增)',
  review_status VARCHAR(20)  DEFAULT 'PENDING'     COMMENT '审核状态 PENDING/APPROVED/REJECTED',
  lifecycle_status VARCHAR(20) DEFAULT 'DRAFT'     COMMENT '生命周期 DRAFT/REVIEW/PUBLISHED/ARCHIVED',
  archived_at   DATETIME     DEFAULT NULL          COMMENT '归档时间(保留期硬删除判定)',
  reviewer_id   BIGINT       DEFAULT NULL          COMMENT '审核人ID',
  reviewed_at   DATETIME     DEFAULT NULL          COMMENT '审核时间',
  effective_from DATETIME    DEFAULT NULL          COMMENT '生效时间(NULL=立即生效)',
  expire_at     DATETIME     DEFAULT NULL          COMMENT '过期时间(NULL=永久)',
  quality_score INT          DEFAULT NULL          COMMENT '质量评分(0-100,NULL=未评估)',
  creator_id    BIGINT       NOT NULL              COMMENT '上传人ID',
  deleted       TINYINT      DEFAULT 0             COMMENT '删除标志 0存在 1删除',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_kb (kb_id),
  KEY idx_creator (creator_id),
  KEY idx_md5 (md5),
  KEY idx_tenant (tenant_id),
  KEY idx_lifecycle (kb_id, lifecycle_status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文档信息';

CREATE TABLE IF NOT EXISTS kb_upload_record (
  id            BIGINT       NOT NULL              COMMENT '主键',
  tenant_id     BIGINT       NOT NULL DEFAULT 0    COMMENT '所属租户ID',
  document_id   BIGINT       DEFAULT NULL          COMMENT '关联文档ID(上传失败时可为空)',
  kb_id         BIGINT       NOT NULL              COMMENT '所属知识库ID',
  original_name VARCHAR(255) NOT NULL              COMMENT '原始文件名',
  file_size     BIGINT       DEFAULT 0             COMMENT '文件大小(字节)',
  file_type     VARCHAR(16)  DEFAULT ''            COMMENT '文件类型',
  upload_status TINYINT      DEFAULT 0             COMMENT '上传状态 0成功 1失败',
  error_msg     VARCHAR(512) DEFAULT ''            COMMENT '失败原因',
  creator_id    BIGINT       NOT NULL              COMMENT '上传人ID',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  PRIMARY KEY (id),
  KEY idx_kb (kb_id),
  KEY idx_document (document_id),
  KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '上传记录';

CREATE TABLE IF NOT EXISTS kb_parse_task (
  id          BIGINT       NOT NULL                COMMENT '主键',
  tenant_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID',
  document_id BIGINT       NOT NULL                COMMENT '文档ID',
  kb_id       BIGINT       NOT NULL                COMMENT '知识库ID',
  status      TINYINT      DEFAULT 0               COMMENT '任务状态 0待处理 1处理中 2成功 3失败',
  retry_count INT          NOT NULL DEFAULT 0      COMMENT '已重试次数（MQ消费失败累计，达max_retries进死信队列）',
  max_retries INT          NOT NULL DEFAULT 3      COMMENT '最大重试次数（与rabbitmq retry.max-attempts对齐）',
  error_msg   VARCHAR(512) DEFAULT ''              COMMENT '失败原因',
  start_time  DATETIME     DEFAULT NULL            COMMENT '开始处理时间',
  end_time    DATETIME     DEFAULT NULL            COMMENT '结束处理时间',
  creator_id  BIGINT       NOT NULL                COMMENT '创建人ID',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_document (document_id),
  KEY idx_status (status),
  KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文档解析任务';

-- ============================================================
-- 三、知识库成员权限表（多知识库权限隔离）
-- ============================================================
CREATE TABLE IF NOT EXISTS kb_member (
  id          BIGINT       NOT NULL                COMMENT '主键',
  tenant_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID',
  kb_id       BIGINT       NOT NULL                COMMENT '知识库ID',
  user_id     BIGINT       NOT NULL                COMMENT '用户ID',
  role        VARCHAR(16)  NOT NULL DEFAULT 'viewer' COMMENT '角色 owner/editor/viewer',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_kb_user (tenant_id, kb_id, user_id),
  KEY idx_user_kb (user_id, kb_id),
  KEY idx_kb (kb_id),
  KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识库成员关系';

-- ============================================================
-- 四、聊天记录表（全部 tenant_id 隔离）
-- ============================================================
CREATE TABLE IF NOT EXISTS chat_session (
  id          BIGINT       NOT NULL                COMMENT '主键',
  tenant_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID',
  kb_id       BIGINT       NOT NULL                COMMENT '关联知识库ID',
  user_id     BIGINT       NOT NULL                COMMENT '所属用户ID',
  title       VARCHAR(128) NOT NULL DEFAULT '新对话' COMMENT '会话标题',
  deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标志 0存在 1删除',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_user_kb (user_id, kb_id),
  KEY idx_kb (kb_id),
  KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '聊天会话';

CREATE TABLE IF NOT EXISTS chat_message (
  id            BIGINT       NOT NULL              COMMENT '主键',
  tenant_id     BIGINT       NOT NULL DEFAULT 0    COMMENT '所属租户ID',
  session_id    BIGINT       NOT NULL              COMMENT '会话ID',
  role          VARCHAR(16)  NOT NULL              COMMENT '消息角色 user/assistant',
  content       MEDIUMTEXT   NOT NULL              COMMENT '消息内容',
  sources_json  JSON         DEFAULT NULL          COMMENT '引用来源(RetrievalResult列表)',
  tokens        INT          DEFAULT NULL          COMMENT '生成token数(预留)',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (id),
  KEY idx_session (session_id, create_time),
  KEY idx_tenant_session (tenant_id, session_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '聊天消息';

-- ============================================================
-- 四-B、语义缓存表（RAG 问答缓存：按 embedding 余弦相似度命中，跳过检索+LLM）
-- ============================================================
CREATE TABLE IF NOT EXISTS rag_semantic_cache (
  id            BIGINT       NOT NULL                COMMENT '主键(雪花)',
  tenant_id     BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID',
  kb_id         BIGINT       NOT NULL                COMMENT '知识库ID',
  question      TEXT         NOT NULL                COMMENT '原始问题文本',
  vector_text   MEDIUMTEXT   NOT NULL                COMMENT '问题向量(逗号分隔float,如0.123,0.456,...)',
  answer        MEDIUMTEXT   NOT NULL                COMMENT '缓存的LLM回答',
  sources_json  JSON         DEFAULT NULL            COMMENT '引用来源(RetrievalResult列表)',
  similarity    DOUBLE       DEFAULT NULL            COMMENT '写入时相似度(调试用)',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间(TTL依据)',
  PRIMARY KEY (id),
  KEY idx_tenant_kb_time (tenant_id, kb_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'RAG语义缓存';

-- ============================================================
-- 五、Prompt 模板三表（元数据 / 版本内容 / 变量定义 分离）
-- ============================================================
-- 5.1 元数据主表：一个 (tenant_id, prompt_code) 一行
CREATE TABLE IF NOT EXISTS prompt_template (
  id          BIGINT       NOT NULL                COMMENT '主键(模板ID,雪花)',
  tenant_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID(0=平台预置)',
  prompt_code VARCHAR(64)  NOT NULL                COMMENT 'Prompt逻辑编码(同租户内唯一)',
  name        VARCHAR(128) NOT NULL                COMMENT '模板名称',
  type        VARCHAR(32)  NOT NULL                COMMENT '类型 system/rag/agent',
  creator_id  BIGINT       DEFAULT NULL            COMMENT '创建人ID',
  deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标志 0存在 1删除',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_code (tenant_id, prompt_code),
  KEY idx_tenant_code (tenant_id, prompt_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Prompt模板元数据(同租户内一code一行)';

-- 5.2 版本内容表：每个版本一行，关联 template_id
CREATE TABLE IF NOT EXISTS prompt_version (
  id          BIGINT       NOT NULL                COMMENT '主键(版本行ID,雪花)',
  template_id BIGINT       NOT NULL                COMMENT '所属模板ID(prompt_template.id)',
  version     INT          NOT NULL                COMMENT '版本号(同模板内递增)',
  content     TEXT         NOT NULL                COMMENT '模板内容(含 {var} 命名占位符)',
  status      VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT草稿/PUBLISHED已发布/ARCHIVED已归档',
  remark      VARCHAR(255) NOT NULL DEFAULT ''      COMMENT '版本说明',
  creator_id  BIGINT       DEFAULT NULL            COMMENT '创建人ID',
  deleted     TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标志 0存在 1删除',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tpl_version (template_id, version),
  KEY idx_tpl_status (template_id, status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Prompt版本内容(同模板多版本)';

-- 5.3 变量定义表：模板级，关联 template_id
CREATE TABLE IF NOT EXISTS prompt_variable (
  id            BIGINT       NOT NULL              COMMENT '主键(雪花)',
  template_id   BIGINT       NOT NULL              COMMENT '所属模板ID(prompt_template.id)',
  var_name      VARCHAR(64)  NOT NULL              COMMENT '变量名(对应 {varName} 占位符)',
  description   VARCHAR(255) NOT NULL DEFAULT ''    COMMENT '变量描述',
  required      TINYINT      NOT NULL DEFAULT 1     COMMENT '是否必填 0否 1是',
  default_value VARCHAR(255) NOT NULL DEFAULT ''    COMMENT '默认值',
  deleted       TINYINT      NOT NULL DEFAULT 0     COMMENT '删除标志 0存在 1删除',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tpl_var (template_id, var_name),
  KEY idx_tpl (template_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Prompt变量定义(模板级)';

-- ============================================================
-- 六、操作日志表（tenant_id 便于租户审计）
-- ============================================================
CREATE TABLE IF NOT EXISTS sys_oper_log (
  id              BIGINT       NOT NULL            COMMENT '主键',
  tenant_id       BIGINT       DEFAULT NULL        COMMENT '操作所属租户(NULL=平台级操作)',
  title           VARCHAR(64)  NOT NULL            COMMENT '模块标题',
  business_type   TINYINT      NOT NULL DEFAULT 0  COMMENT '业务类型 0其它1新增2修改3删除4导出5导入6登录',
  method          VARCHAR(256) DEFAULT ''          COMMENT '方法名',
  request_url     VARCHAR(256) DEFAULT ''          COMMENT '请求URL',
  request_param   TEXT         DEFAULT NULL        COMMENT '请求参数(JSON)',
  response_result TEXT         DEFAULT NULL        COMMENT '响应结果(截断)',
  status          TINYINT      NOT NULL DEFAULT 0  COMMENT '状态 0正常 1异常',
  error_msg       TEXT         DEFAULT NULL        COMMENT '错误信息',
  oper_ip         VARCHAR(64)  DEFAULT ''          COMMENT '操作IP',
  oper_user       VARCHAR(64)  DEFAULT ''          COMMENT '操作用户',
  cost_time       BIGINT       DEFAULT 0           COMMENT '耗时(ms)',
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (id),
  KEY idx_oper_time (create_time),
  KEY idx_oper_user (oper_user),
  KEY idx_tenant_time (tenant_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志';

-- ============================================================
-- 七、AI 模型调用日志表（运营管理：记录每次模型调用，统计调用/Token/费用）
-- ============================================================
CREATE TABLE IF NOT EXISTS ai_call_log (
  id                 BIGINT        NOT NULL            COMMENT '主键(雪花)',
  tenant_id          BIGINT        NOT NULL            COMMENT '租户ID',
  user_id            BIGINT        DEFAULT NULL        COMMENT '用户ID(系统调用为空)',
  username           VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '用户名(冗余便于展示)',
  module             VARCHAR(32)   NOT NULL            COMMENT '业务模块 rag_chat/agent/workflow/prompt_test/embedding',
  biz_type           VARCHAR(16)   NOT NULL            COMMENT '调用类型 CHAT/EMBEDDING/RERANK',
  model_name         VARCHAR(64)   NOT NULL            COMMENT '模型名',
  prompt_tokens      INT           NOT NULL DEFAULT 0  COMMENT '输入token',
  completion_tokens  INT           NOT NULL DEFAULT 0  COMMENT '输出token',
  total_tokens       INT           NOT NULL DEFAULT 0  COMMENT '总token',
  duration_ms        INT           NOT NULL DEFAULT 0  COMMENT '耗时(毫秒)',
  cost               DECIMAL(12,6) NOT NULL DEFAULT 0  COMMENT '费用(元)',
  status             VARCHAR(16)   NOT NULL DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED',
  error_msg          VARCHAR(512)  DEFAULT NULL        COMMENT '失败原因(截断)',
  create_time        DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间',
  PRIMARY KEY (id),
  KEY idx_tenant_time (tenant_id, create_time),
  KEY idx_tenant_user_time (tenant_id, user_id, create_time),
  KEY idx_tenant_model_time (tenant_id, model_name, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI模型调用日志';

-- ============================================================
-- 七-2、AI 运维中心表（基础设施指标 / 分布式追踪 / 告警规则）
-- ============================================================
CREATE TABLE IF NOT EXISTS infra_metric (
  id            BIGINT       NOT NULL                COMMENT '主键(雪花)',
  tenant_id     BIGINT       NOT NULL                COMMENT '租户ID',
  resource      VARCHAR(64)  NOT NULL                COMMENT '资源 milvus:search/es:search/mq:parse',
  action        VARCHAR(32)  NOT NULL DEFAULT 'search' COMMENT '动作 search/consume/store',
  duration_ms   INT          NOT NULL DEFAULT 0      COMMENT '耗时(毫秒)',
  success       TINYINT      NOT NULL DEFAULT 1      COMMENT '1成功 0失败',
  error_msg     VARCHAR(512) DEFAULT NULL            COMMENT '失败原因(截断)',
  metadata      VARCHAR(1024) DEFAULT NULL           COMMENT '附加元数据 JSON(kbId/topK 等)',
  create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (id),
  KEY idx_tenant_resource_time (tenant_id, resource, create_time),
  KEY idx_tenant_time (tenant_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '基础设施指标(append-only)';

CREATE TABLE IF NOT EXISTS ops_trace (
  id              BIGINT       NOT NULL              COMMENT '主键(雪花)',
  tenant_id       BIGINT       NOT NULL              COMMENT '租户ID',
  trace_id        VARCHAR(64)  NOT NULL              COMMENT '追踪ID(同次请求一致)',
  span_id         VARCHAR(64)  NOT NULL              COMMENT '当前 span ID',
  parent_span_id  VARCHAR(64)  DEFAULT NULL          COMMENT '父 span ID(ROOT 为空)',
  span_name       VARCHAR(128) NOT NULL              COMMENT 'span 名 rag.ask/milvus.search/llm.chat',
  span_type       VARCHAR(16)  NOT NULL              COMMENT 'ROOT/SEARCH/LLM/TOOL/MQ',
  start_time      DATETIME     NOT NULL              COMMENT '开始时间',
  duration_ms     INT          NOT NULL DEFAULT 0    COMMENT '耗时(毫秒)',
  status          VARCHAR(16)  NOT NULL DEFAULT 'OK' COMMENT 'OK/ERROR',
  attributes_json VARCHAR(2048) DEFAULT NULL         COMMENT '属性 JSON(kbId/topK/tokenUsage 等)',
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '入库时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_span (tenant_id, span_id),
  KEY idx_tenant_trace (tenant_id, trace_id),
  KEY idx_tenant_time (tenant_id, start_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '分布式追踪span';

CREATE TABLE IF NOT EXISTS alert_rule (
  id              BIGINT       NOT NULL              COMMENT '主键(雪花)',
  tenant_id       BIGINT       NOT NULL              COMMENT '租户ID',
  name            VARCHAR(128) NOT NULL              COMMENT '规则名',
  resource        VARCHAR(64)  NOT NULL              COMMENT '资源 对齐AlertService.source',
  metric          VARCHAR(32)  NOT NULL              COMMENT '指标 calls/error_rate/latency_p95/token_usage',
  operator        VARCHAR(8)   NOT NULL              COMMENT '比较符 GT/LT/GTE/LTE',
  threshold       DECIMAL(14,2) NOT NULL             COMMENT '阈值',
  window_minutes  INT          NOT NULL DEFAULT 5    COMMENT '统计窗口(分钟)',
  level           VARCHAR(16)  NOT NULL DEFAULT 'WARN' COMMENT '触发级别 INFO/WARN/CRITICAL',
  enabled         TINYINT      NOT NULL DEFAULT 1    COMMENT '1启用 0停用',
  deleted         TINYINT      DEFAULT 0             COMMENT '删除标志 0存在 1删除',
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  KEY idx_tenant_enabled (tenant_id, enabled)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '告警规则';

-- ============================================================
-- 种子数据
-- ============================================================
-- 平台超级管理员租户（0 号，保留）
INSERT INTO sys_tenant (id, tenant_code, tenant_name, status, max_storage_mb, max_users) VALUES
  (0, 'platform', '平台运维租户', 0, 1048576, 100),
  (1, 'demo', '演示租户（默认）', 0, 20480, 100)
ON DUPLICATE KEY UPDATE tenant_name = VALUES(tenant_name);

-- 平台级角色（tenant_id=0，role_scope=P）+ 租户级默认角色（tenant_id=1，演示租户）
INSERT INTO sys_role (id, tenant_id, role_name, role_key, role_scope, status) VALUES
  (1, 0, '平台超级管理员', 'platform_super_admin', 'P', 0),
  (2, 1, '租户管理员',       'tenant_admin',         'T', 0),
  (3, 1, '普通用户',         'tenant_user',          'T', 0)
ON DUPLICATE KEY UPDATE role_name = VALUES(role_name);

INSERT INTO sys_menu (id, parent_id, menu_name, menu_type, perms, path, component, icon, sort_order) VALUES
  (1,  0, '系统管理',   'M', NULL,                   '/system',    '', 'Setting',        1),
  (2,  1, '用户管理',   'C', 'system:user:list',     'user',       '', 'User',           1),
  (3,  2, '用户新增',   'F', 'system:user:add',      '',           '', '',               1),
  (4,  2, '用户修改',   'F', 'system:user:edit',     '',           '', '',               2),
  (5,  2, '用户删除',   'F', 'system:user:remove',   '',           '', '',               3),
  (6,  1, '角色管理',   'C', 'system:role:list',     'role',       '', 'UserFilled',     2),
  (7,  1, '菜单管理',   'C', 'system:menu:list',     'menu',       '', 'Menu',           3),
  (8,  1, '操作日志',   'C', 'system:operlog:list',  'oper-log',   '', 'Document',       4),
  (10, 0, '知识库管理', 'M', NULL,                   '/knowledge', '', 'Collection',     2),
  (11, 10, '知识库',    'C', 'knowledge:kb:list',    'kb',         '', 'Files',          1),
  (12, 10, '文档管理',  'C', 'knowledge:document:list','document', '', 'Document',       2),
  (13, 10, '解析任务',  'C', 'knowledge:task:list',  'task',       '', 'Loading',        3),
  (14,  0, 'RAG对话',   'C', 'chat:ask',             '/chat',      '', 'ChatDotRound',   3),
  (15,  0, 'Prompt模板','C', 'prompt:list',          '/prompt',    '', 'EditPen',        4),
  (20,  0, '租户管理',  'C', 'system:tenant:list',   'tenant',     '', 'OfficeBuilding', 99)
ON DUPLICATE KEY UPDATE menu_name = VALUES(menu_name);

-- 演示租户给平台与租户角色授权全部菜单
INSERT INTO sys_tenant_menu (tenant_id, menu_id) VALUES
  (1,1),(1,2),(1,3),(1,4),(1,5),(1,6),(1,7),(1,8),(1,10),(1,11),(1,12),(1,13),(1,14),(1,15)
ON DUPLICATE KEY UPDATE tenant_id = tenant_id;
-- 平台租户（0）额外多一个租户管理菜单
INSERT INTO sys_tenant_menu (tenant_id, menu_id) VALUES
  (0,1),(0,2),(0,3),(0,4),(0,5),(0,6),(0,7),(0,8),(0,10),(0,11),(0,12),(0,13),(0,14),(0,15),(0,20)
ON DUPLICATE KEY UPDATE tenant_id = tenant_id;

-- 演示租户用户：admin(demo租户管理员) + user1/demo普通用户（tenant_id=1）
-- 密码均为 123456（BCrypt 哈希）
INSERT INTO sys_user (id, tenant_id, username, password, nickname, status) VALUES
  (1, 0, 'sa',        '$2b$10$DLdFsHWWKhU/9YK0ipt6gez5lUrAeWKLEZ0Rr3Qbj2W4kusyN/oQG', '平台超级管理员', 0),
  (2, 1, 'admin',     '$2b$10$DLdFsHWWKhU/9YK0ipt6gez5lUrAeWKLEZ0Rr3Qbj2W4kusyN/oQG', '演示租户管理员', 0),
  (3, 1, 'user1',     '$2b$10$DLdFsHWWKhU/9YK0ipt6gez5lUrAeWKLEZ0Rr3Qbj2W4kusyN/oQG', '演示用户1', 0)
ON DUPLICATE KEY UPDATE nickname = VALUES(nickname);

INSERT INTO sys_user_role (tenant_id, user_id, role_id) VALUES
  (0, 1, 1),  -- sa(tenant=0) 拥有平台超级管理员
  (1, 2, 2),  -- admin(tenant=1) 是租户管理员
  (1, 3, 3)   -- user1(tenant=1) 是普通用户
ON DUPLICATE KEY UPDATE user_id = user_id;

-- 角色菜单授权
-- 1. platform_super_admin（id=1, tenant=0）：含租户管理菜单20
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id) VALUES
  (0,1,1),(0,1,2),(0,1,3),(0,1,4),(0,1,5),(0,1,6),(0,1,7),(0,1,8),(0,1,10),(0,1,11),(0,1,12),(0,1,13),(0,1,14),(0,1,15),(0,1,20)
ON DUPLICATE KEY UPDATE role_id = role_id;
-- 2. tenant_admin（id=2, tenant=1）：除租户管理外全部菜单
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id) VALUES
  (1,2,1),(1,2,2),(1,2,3),(1,2,4),(1,2,5),(1,2,6),(1,2,7),(1,2,8),(1,2,10),(1,2,11),(1,2,12),(1,2,13),(1,2,14),(1,2,15)
ON DUPLICATE KEY UPDATE role_id = role_id;
-- 3. tenant_user（id=3, tenant=1）：仅知识库+对话
INSERT INTO sys_role_menu (tenant_id, role_id, menu_id) VALUES
  (1,3,10),(1,3,11),(1,3,12),(1,3,13),(1,3,14)
ON DUPLICATE KEY UPDATE role_id = role_id;

-- Prompt 模板种子（三表：平台预置 tenant=0，status=PUBLISHED 即生效；{context}/{goal} 命名占位符）
-- 1) 元数据主表（template id 2001/2002/2003）
INSERT INTO prompt_template (id, tenant_id, prompt_code, name, type, creator_id) VALUES
  (2001, 0, 'rag_system_prompt', 'RAG 系统提示', 'rag', NULL),
  (2002, 0, 'default_system',    '通用系统人设',  'system', NULL),
  (2003, 0, 'agent_analysis',    'Agent 分析提示','agent', NULL)
ON DUPLICATE KEY UPDATE name = VALUES(name);

-- 2) 版本内容表（version id 复用 1001/1002/1003，v1 PUBLISHED）
INSERT INTO prompt_version (id, template_id, version, content, status, remark, creator_id) VALUES
  (1001, 2001, 1,
   '你是企业知识库助手。请严格依据下方【参考资料】回答用户问题。\n回答规则：\n1. 答案必须基于参考资料，不得编造或引入资料外的信息。\n2. 若参考资料不足以回答，请直接回复"知识库中暂无相关信息"。\n3. 必须标注引用来源：引用某条资料内容的句子末尾用 [编号] 标注（如 [1] 或 [1][3]），编号对应【参考资料】中的条目顺序；未引用资料时不标注。\n\n【参考资料】\n{context}',
   'PUBLISHED', '初始版本', NULL),
  (1002, 2002, 1,
   '你是企业知识库助手，请以专业、准确、简洁的方式回答用户问题。当信息不足时如实告知，不编造内容。',
   'PUBLISHED', '初始版本', NULL),
  (1003, 2003, 1,
   '你是一名资深业务分析师。请基于已知信息，对以下目标进行结构化分析，输出关键发现、风险点与建议：\n{goal}',
   'PUBLISHED', '初始版本', NULL)
ON DUPLICATE KEY UPDATE content = VALUES(content);

-- 3) 变量定义表（template 2001→context；template 2003→goal；2002 无变量）
INSERT INTO prompt_variable (id, template_id, var_name, description, required, default_value) VALUES
  (3001, 2001, 'context', '检索到的编号上下文文本', 1, ''),
  (3002, 2003, 'goal',    '分析目标',               1, '')
ON DUPLICATE KEY UPDATE var_name = VALUES(var_name);

-- =====================================================================
-- 文档级权限（RBAC + 数据权限）
-- =====================================================================

-- 1. 文档可见性与继承开关
ALTER TABLE kb_document
  ADD COLUMN visibility          CHAR(1)     NOT NULL DEFAULT 'P'
        COMMENT '可见性 P=公开 R=私有 T=保护级' AFTER chunk_count;
ALTER TABLE kb_document
  ADD COLUMN inherit_kb_permission TINYINT(1) NOT NULL DEFAULT 1
        COMMENT '是否继承KB角色权限 1=是 0=仅ACL' AFTER visibility;
ALTER TABLE kb_document
  ADD KEY idx_kb_visibility (kb_id, visibility, creator_id);

-- 2. 文档级 ACL 表
CREATE TABLE IF NOT EXISTS kb_doc_acl (
  id            BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
  tenant_id     BIGINT       NOT NULL DEFAULT 1,
  doc_id        BIGINT       NOT NULL,
  subject_type  CHAR(1)      NOT NULL COMMENT 'U=用户 R=角色 D=部门',
  subject_id    BIGINT       NOT NULL COMMENT 'user_id/role_id/dept_id',
  permission    VARCHAR(32)  NOT NULL COMMENT 'VIEW/EDIT/DELETE/DOWNLOAD/SHARE',
  effect        CHAR(1)      NOT NULL DEFAULT 'A' COMMENT 'A=ALLOW D=DENY',
  grant_by      BIGINT                COMMENT '授权操作人 user_id',
  expire_time   DATETIME              COMMENT '授权到期时间 NULL=永久',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT(1)   NOT NULL DEFAULT 0,
  UNIQUE KEY uk_doc_subject_perm (tenant_id, doc_id, subject_type, subject_id, permission, deleted),
  KEY idx_doc     (tenant_id, doc_id),
  KEY idx_subject (tenant_id, subject_type, subject_id, expire_time)
) ENGINE=InnoDB COMMENT='文档级ACL：主体×权限位×效果×可选过期';

-- 3. 文档访问审计日志
CREATE TABLE IF NOT EXISTS kb_doc_audit_log (
  id          BIGINT       NOT NULL PRIMARY KEY,
  tenant_id   BIGINT       NOT NULL,
  user_id     BIGINT       NOT NULL,
  doc_id      BIGINT       NOT NULL,
  action      VARCHAR(32)  NOT NULL COMMENT 'VIEW/DOWNLOAD/EDIT/DELETE/SHARE/SEARCH_HIT',
  result      CHAR(1)      NOT NULL COMMENT 'A=放行 D=拒绝',
  pass_reason VARCHAR(256)          COMMENT '放行原因 ACL_ALLOW/KB_OWNER/EDITOR/CREATOR/TENANT_ADMIN',
  deny_reason VARCHAR(256)          COMMENT '拒绝原因 DENY_RULE/NO_INHERIT/OUT_OF_KB_ROLE/PRIVATE_DOC',
  ip          VARCHAR(64),
  user_agent  VARCHAR(512),
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_user_time (tenant_id, user_id, create_time),
  KEY idx_doc_time  (tenant_id, doc_id, create_time),
  KEY idx_result     (tenant_id, result, create_time)
) ENGINE=InnoDB COMMENT='文档访问审计日志';

-- -------- 演示种子：文档权限示例 --------
-- 假设 kb_document 中已有 id=1 (PUBLIC) 和 id=2 (PRIVATE)，这里给 PRIVATE=2 给 user1 加 VIEW：
-- （kb_document 本身演示数据在 upload 后由系统自动生成，此处仅给 ACL 演示用）
INSERT IGNORE INTO kb_doc_acl (id, tenant_id, doc_id, subject_type, subject_id, permission, effect, grant_by)
VALUES
  (1001, 1, 2, 'U', 3, 'VIEW',     'A', 2), -- 演示租户 user1(id=3) 可 VIEW 文档2(PRIVATE)
  (1002, 1, 2, 'U', 3, 'DOWNLOAD', 'A', 2); -- 同时授权 DOWNLOAD（正交动作）


-- ====================================================================
-- 知识治理模块（文档去重 / 版本管理 / 审核记录 / 质量评分）
-- ====================================================================

-- 1. 文档指纹（MD5 精确 + SimHash 近似去重的基础数据）
CREATE TABLE IF NOT EXISTS document_fingerprint (
  id             BIGINT       NOT NULL                COMMENT '主键',
  tenant_id      BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID',
  kb_id          BIGINT       NOT NULL                COMMENT '所属知识库ID',
  doc_id         BIGINT       NOT NULL                COMMENT '文档ID',
  md5            VARCHAR(64)  DEFAULT ''              COMMENT 'MD5(与 kb_document.md5 冗余便于去重查询)',
  simhash        BIGINT       DEFAULT NULL            COMMENT 'SimHash 64位指纹(NULL=未计算)',
  content_length INT          DEFAULT 0               COMMENT '解析后纯文本长度',
  token_count    INT          DEFAULT 0               COMMENT '切片数量(近似 token 估计)',
  create_time    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_doc (doc_id),
  KEY idx_kb_md5 (kb_id, md5),
  KEY idx_kb_simhash (kb_id, simhash),
  KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文档指纹(去重基础)';

-- 2. 文档重复关系（检测出的重复对）
CREATE TABLE IF NOT EXISTS document_duplicate (
  id          BIGINT        NOT NULL                COMMENT '主键',
  tenant_id   BIGINT        NOT NULL DEFAULT 0      COMMENT '所属租户ID',
  kb_id       BIGINT        NOT NULL                COMMENT '所属知识库ID',
  doc_id1     BIGINT        NOT NULL                COMMENT '文档A(小ID)',
  doc_id2     BIGINT        NOT NULL                COMMENT '文档B(大ID)',
  similarity  DECIMAL(6,4)  NOT NULL                COMMENT '相似度0~1',
  dup_type    VARCHAR(16)   NOT NULL                COMMENT 'EXACT精确/NEAR近似',
  status      VARCHAR(20)   NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING待处理/CONFIRMED确认重复/IGNORED忽略',
  create_time DATETIME      DEFAULT CURRENT_TIMESTAMP COMMENT '检测时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_pair (doc_id1, doc_id2),
  KEY idx_kb_status (kb_id, status),
  KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文档重复关系';

-- 3. 文档版本历史（同文档多版本，主表 kb_document.version 指向当前版本）
CREATE TABLE IF NOT EXISTS document_version (
  id          BIGINT       NOT NULL                COMMENT '主键',
  tenant_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID',
  kb_id       BIGINT       NOT NULL                COMMENT '所属知识库ID',
  doc_id      BIGINT       NOT NULL                COMMENT '文档ID',
  version     INT          NOT NULL                COMMENT '版本号',
  stored_name VARCHAR(255) NOT NULL                COMMENT '该版本存储文件名',
  file_path   VARCHAR(512) NOT NULL                COMMENT '该版本存储路径',
  file_size   BIGINT       DEFAULT 0               COMMENT '文件大小(字节)',
  md5         VARCHAR(64)  DEFAULT ''              COMMENT '该版本MD5',
  change_log  VARCHAR(512) DEFAULT ''              COMMENT '版本变更说明',
  creator_id  BIGINT       NOT NULL                COMMENT '上传人ID',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '版本创建时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_doc_version (doc_id, version),
  KEY idx_kb (kb_id),
  KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文档版本历史';

-- 4. 文档审核记录（审核动作审计流水）
CREATE TABLE IF NOT EXISTS document_review (
  id          BIGINT       NOT NULL                COMMENT '主键',
  tenant_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID',
  kb_id       BIGINT       NOT NULL                COMMENT '所属知识库ID',
  doc_id      BIGINT       NOT NULL                COMMENT '文档ID',
  reviewer_id BIGINT       NOT NULL                COMMENT '审核人ID',
  action      VARCHAR(16)  NOT NULL                COMMENT 'SUBMIT提交/APPROVE通过/REJECT驳回',
  comment     VARCHAR(512) DEFAULT ''              COMMENT '审核意见',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '审核时间',
  PRIMARY KEY (id),
  KEY idx_doc (doc_id, create_time),
  KEY idx_kb (kb_id),
  KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文档审核记录';

-- 5. 文档质量评分（4维评分 + 总分）
CREATE TABLE IF NOT EXISTS document_quality (
  id          BIGINT       NOT NULL                COMMENT '主键',
  tenant_id   BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID',
  kb_id       BIGINT       NOT NULL                COMMENT '所属知识库ID',
  doc_id      BIGINT       NOT NULL                COMMENT '文档ID',
  score       INT          NOT NULL                COMMENT '总分0-100',
  completeness INT         NOT NULL DEFAULT 0      COMMENT '完整性0-25',
  freshness   INT          NOT NULL DEFAULT 0      COMMENT '时效性0-25',
  structure   INT          NOT NULL DEFAULT 0      COMMENT '结构性0-25',
  coverage    INT          NOT NULL DEFAULT 0      COMMENT '覆盖度0-25',
  summary     VARCHAR(512) DEFAULT ''              COMMENT '评分说明',
  evaluator   VARCHAR(16)  NOT NULL DEFAULT 'RULE' COMMENT 'RULE规则/LLM模型',
  create_time DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '评分时间',
  PRIMARY KEY (id),
  KEY idx_doc (doc_id, create_time),
  KEY idx_kb_score (kb_id, score),
  KEY idx_tenant (tenant_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '文档质量评分';

-- 6. 知识治理策略（每KB一份：强制审核/自动归档/保留期/审核角色/审核超时）
CREATE TABLE IF NOT EXISTS knowledge_policy (
  id                  BIGINT       NOT NULL              COMMENT '主键',
  tenant_id           BIGINT       NOT NULL DEFAULT 0    COMMENT '所属租户ID',
  kb_id               BIGINT       NOT NULL              COMMENT '所属知识库ID',
  require_review      TINYINT(1)   NOT NULL DEFAULT 1    COMMENT '是否强制审核 1=须审核后发布 0=可直接发布',
  auto_archive_days   INT          DEFAULT NULL          COMMENT '发布后自动归档天数 NULL=不自动归档',
  retention_days      INT          DEFAULT NULL          COMMENT '归档后保留天数 NULL=永久 达到即硬删除',
  approver_roles      VARCHAR(256) DEFAULT NULL          COMMENT '允许审核角色 逗号分隔 KB_OWNER,KB_EDITOR',
  review_expire_hours INT          DEFAULT NULL          COMMENT '审核超时小时数 NULL=不超时',
  create_time         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_kb (tenant_id, kb_id),
  KEY idx_tenant_kb (tenant_id, kb_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识治理策略(每KB一份)';


-- ====================================================================
-- Agent 工作流状态模型（任务型 Agent 引擎：Planner→Knowledge→Analysis→Report）
-- ====================================================================

-- 1. Agent 任务（用户发起的一个分析任务，对应一次完整工作流执行）
CREATE TABLE IF NOT EXISTS agent_task (
  id              BIGINT       NOT NULL PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL,
  user_id         BIGINT       NOT NULL,
  kb_id           BIGINT       NOT NULL COMMENT '知识库ID（检索范围，需 viewer 及以上权限）',
  session_id      BIGINT                COMMENT '关联会话ID（可空，便于关联对话上下文）',
  goal            TEXT         NOT NULL COMMENT '用户原始目标（如：分析2025销售政策相比2024变化）',
  status          VARCHAR(16)  NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/PLANNING/EXECUTING/COMPLETED/FAILED/CANCELED',
  plan            TEXT                  COMMENT 'DAG 计划 JSON（PlannerAgent 产出）',
  result          MEDIUMTEXT            COMMENT '最终报告（ReportAgent 产出）',
  step_count      INT          NOT NULL DEFAULT 0,
  tool_call_count INT          NOT NULL DEFAULT 0,
  token_usage     INT          NOT NULL DEFAULT 0,
  error_code      VARCHAR(64),
  error_msg       VARCHAR(1024),
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  finished_time   DATETIME,
  deleted         TINYINT(1)   NOT NULL DEFAULT 0,
  KEY idx_user_time (tenant_id, user_id, create_time),
  KEY idx_kb_time  (tenant_id, kb_id, create_time),
  KEY idx_status   (tenant_id, status)
) ENGINE=InnoDB COMMENT='Agent 任务（一次工作流执行）';

-- 2. Agent 步骤（每个 Agent 执行 = 1 行，记录耗时/token/状态）
CREATE TABLE IF NOT EXISTS agent_step (
  id            BIGINT       NOT NULL PRIMARY KEY,
  tenant_id     BIGINT       NOT NULL,
  task_id       BIGINT       NOT NULL,
  step_index    INT          NOT NULL COMMENT '执行顺序（从1开始）',
  agent_type    VARCHAR(16)  NOT NULL COMMENT 'PLANNER/KNOWLEDGE/ANALYSIS/REPORT',
  status        VARCHAR(16)  NOT NULL COMMENT 'PENDING/RUNNING/SUCCESS/FAILED',
  input_summary TEXT                  COMMENT '输入摘要（goal / query 等）',
  output_summary TEXT                 COMMENT '输出摘要',
  token_usage   INT          NOT NULL DEFAULT 0,
  duration_ms   BIGINT       NOT NULL DEFAULT 0,
  error_msg     VARCHAR(1024),
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  started_at    DATETIME,
  finished_at   DATETIME,
  KEY idx_task (tenant_id, task_id, step_index)
) ENGINE=InnoDB COMMENT='Agent 步骤执行记录（审计/回放）';

-- 3. Agent 消息（LLM/Tool 完整 IO，用于审计与回放）
CREATE TABLE IF NOT EXISTS agent_message (
  id            BIGINT       NOT NULL PRIMARY KEY,
  tenant_id     BIGINT       NOT NULL,
  task_id       BIGINT       NOT NULL,
  step_id       BIGINT                COMMENT '关联步骤ID（可空）',
  role          VARCHAR(16)  NOT NULL COMMENT 'system/user/assistant/tool',
  content       MEDIUMTEXT   NOT NULL,
  tool_name     VARCHAR(128)           COMMENT '工具名（role=tool 时）',
  token_usage   INT          NOT NULL DEFAULT 0,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_task_time (tenant_id, task_id, create_time)
) ENGINE=InnoDB COMMENT='Agent 消息流（LLM/Tool IO 审计）';

-- 4. Agent 产物（结构化产物，跨步引用：plan/evidences/analysis/report）
CREATE TABLE IF NOT EXISTS agent_artifact (
  id            BIGINT       NOT NULL PRIMARY KEY,
  tenant_id     BIGINT       NOT NULL,
  task_id       BIGINT       NOT NULL,
  step_id       BIGINT,
  artifact_type VARCHAR(32)  NOT NULL COMMENT 'PLAN/EVIDENCES/ANALYSIS/REPORT',
  payload       MEDIUMTEXT   NOT NULL COMMENT 'JSON 产物',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_task_type (tenant_id, task_id, artifact_type)
) ENGINE=InnoDB COMMENT='Agent 产物（结构化，跨步引用）';

-- ====================================================================
-- Agent 记忆中心（会话/摘要/长期事实/向量记忆）
-- ====================================================================

CREATE TABLE IF NOT EXISTS agent_memory (
  id               BIGINT       NOT NULL PRIMARY KEY,
  tenant_id        BIGINT       NOT NULL DEFAULT 1,
  user_id          BIGINT                COMMENT '用户ID（长期记忆归属）',
  session_id       BIGINT                COMMENT '会话ID（会话/摘要记忆归属）',
  memory_type      VARCHAR(32)  NOT NULL COMMENT 'CONVERSATION/SUMMARY/LONG_TERM/VECTOR',
  content          TEXT         NOT NULL COMMENT '记忆内容',
  source_session_id BIGINT               COMMENT '来源会话ID（长期事实溯源）',
  create_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted          TINYINT(1)   NOT NULL DEFAULT 0,
  KEY idx_user_type (user_id, memory_type, create_time),
  KEY idx_session (session_id)
) ENGINE=InnoDB COMMENT='Agent 记忆（会话/摘要/长期事实/向量）';

-- ====================================================================
-- Agent 编排引擎（图编排 + Saga 补偿）
-- ====================================================================

CREATE TABLE IF NOT EXISTS agent_node_run (
  id              BIGINT       NOT NULL PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL,
  task_id         BIGINT       NOT NULL COMMENT '关联 agent_task.id',
  node_id         VARCHAR(64)  NOT NULL COMMENT '图节点ID',
  node_name       VARCHAR(128)          COMMENT '节点名称',
  agent_type      VARCHAR(32)           COMMENT 'Agent 类型',
  run_index       INT          NOT NULL DEFAULT 0 COMMENT '执行序号',
  attempt         INT          NOT NULL DEFAULT 1 COMMENT '当前重试次数',
  status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCEEDED/FAILED',
  input_summary   VARCHAR(512)          COMMENT '输入摘要',
  output_summary  VARCHAR(512)          COMMENT '输出摘要',
  timeout_ms      INT                   COMMENT '节点超时（毫秒）',
  token_usage     INT          NOT NULL DEFAULT 0,
  duration_ms     BIGINT                COMMENT '执行耗时（毫秒）',
  error_msg       TEXT                  COMMENT '失败原因',
  started_at      DATETIME,
  finished_at     DATETIME,
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_task (tenant_id, task_id),
  KEY idx_status (tenant_id, status)
) ENGINE=InnoDB COMMENT='Agent 编排节点执行记录';

CREATE TABLE IF NOT EXISTS agent_compensation (
  id              BIGINT       NOT NULL PRIMARY KEY,
  tenant_id       BIGINT       NOT NULL,
  task_id         BIGINT       NOT NULL COMMENT '关联 agent_task.id',
  node_id         VARCHAR(64)  NOT NULL COMMENT '图节点ID',
  run_id          BIGINT                COMMENT '关联 agent_node_run.id',
  description     VARCHAR(512)          COMMENT '补偿操作描述',
  status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/COMPENSATED/SKIPPED',
  error_msg       TEXT                  COMMENT '补偿失败原因',
  duration_ms     BIGINT                COMMENT '补偿耗时（毫秒）',
  started_at      DATETIME,
  finished_at     DATETIME,
  create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_task (tenant_id, task_id),
  KEY idx_status (tenant_id, status)
) ENGINE=InnoDB COMMENT='Agent 编排 Saga 补偿记录';

-- ====================================================================
-- Workflow Agent（可控流程引擎：声明式流程定义 + 节点级状态保存 + 失败重试 + 人工审批）
-- 与自主式 AgentExecutor 并列：流程定义/任务/节点执行记录三表。
-- ====================================================================

-- 1. 流程定义（一个 code+version = 一份不可变流程模板）
CREATE TABLE IF NOT EXISTS workflow_definition (
  id            BIGINT       NOT NULL PRIMARY KEY,
  tenant_id     BIGINT       NOT NULL,
  code          VARCHAR(64)  NOT NULL COMMENT '流程编码（如 policy_analysis）',
  name          VARCHAR(128) NOT NULL,
  version       INT          NOT NULL DEFAULT 1,
  status        VARCHAR(16)  NOT NULL DEFAULT 'ENABLED' COMMENT 'ENABLED/DISABLED',
  definition    MEDIUMTEXT   NOT NULL COMMENT '流程定义 JSON（nodes 列表）',
  description   VARCHAR(512),
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT(1)   NOT NULL DEFAULT 0,
  UNIQUE KEY uk_tenant_code_version (tenant_id, code, version),
  KEY idx_tenant_code (tenant_id, code)
) ENGINE=InnoDB COMMENT='Workflow 流程定义（模板）';

-- 2. 流程任务（一次流程执行 = 1 行；context_json 保存节点间变量，current_node 为断点）
CREATE TABLE IF NOT EXISTS workflow_task (
  id            BIGINT       NOT NULL PRIMARY KEY,
  tenant_id     BIGINT       NOT NULL,
  user_id       BIGINT       NOT NULL,
  definition_id BIGINT       NOT NULL,
  kb_id         BIGINT                COMMENT '知识库ID（检索范围，可空）',
  business_key  VARCHAR(128)          COMMENT '业务键（外部关联，可空）',
  goal          TEXT         NOT NULL COMMENT '流程目标/输入主题',
  status        VARCHAR(16)  NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/RUNNING/WAITING_HUMAN/COMPLETED/FAILED/CANCELED',
  current_node  VARCHAR(64)           COMMENT '当前节点ID（断点/恢复点）',
  context_json  MEDIUMTEXT            COMMENT '上下文变量 JSON（节点产物累计，状态保存核心）',
  result        MEDIUMTEXT            COMMENT '流程最终结果',
  node_count    INT          NOT NULL DEFAULT 0,
  retry_count   INT          NOT NULL DEFAULT 0,
  token_usage   INT          NOT NULL DEFAULT 0,
  error_code    VARCHAR(64),
  error_msg     VARCHAR(1024),
  finished_time DATETIME,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  deleted       TINYINT(1)   NOT NULL DEFAULT 0,
  KEY idx_tenant_user (tenant_id, user_id),
  KEY idx_tenant_def  (tenant_id, definition_id),
  KEY idx_status      (tenant_id, status)
) ENGINE=InnoDB COMMENT='Workflow 流程任务（运行实例）';

-- 3. 节点执行记录（每个节点每次执行 = 1 行；HUMAN 记录审批结果，attempt 记录重试次数）
CREATE TABLE IF NOT EXISTS workflow_node_run (
  id             BIGINT       NOT NULL PRIMARY KEY,
  tenant_id      BIGINT       NOT NULL,
  task_id        BIGINT       NOT NULL,
  node_id        VARCHAR(64)  NOT NULL,
  node_name      VARCHAR(128),
  node_type      VARCHAR(16)  NOT NULL COMMENT 'START/TOOL/LLM/HUMAN/END',
  run_index      INT          NOT NULL COMMENT '执行序号（从1开始）',
  attempt        INT          NOT NULL DEFAULT 1 COMMENT '重试尝试次数',
  status         VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT 'PENDING/RUNNING/SUCCESS/FAILED/WAITING_HUMAN/SKIPPED/CANCELED',
  input_json     MEDIUMTEXT            COMMENT '输入快照',
  output_json    MEDIUMTEXT            COMMENT '输出快照',
  approved       TINYINT(1)            COMMENT '审批结果 1通过 0驳回（HUMAN）',
  approver_user_id BIGINT               COMMENT '审批人ID（HUMAN）',
  approval_comment VARCHAR(1024)        COMMENT '审批意见（HUMAN）',
  token_usage    INT          NOT NULL DEFAULT 0,
  duration_ms    BIGINT       NOT NULL DEFAULT 0,
  error_msg      VARCHAR(1024),
  started_at     DATETIME,
  finished_at    DATETIME,
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  KEY idx_tenant_task (tenant_id, task_id),
  KEY idx_task_node  (task_id, node_id, attempt)
) ENGINE=InnoDB COMMENT='Workflow 节点执行记录（审计/回放/重试）';

-- ====================================================================
-- 自定义 Agent（用户可定义助手：知识库/自定义内容/日志三类数据源 × 单步流式/多步流程两种执行模型）
-- ====================================================================

-- 1. 自定义 Agent 定义表（一个用户可创建多个助手）
CREATE TABLE IF NOT EXISTS agent_definition (
  id             BIGINT       NOT NULL PRIMARY KEY,
  tenant_id      BIGINT       NOT NULL                COMMENT '租户ID（多租户隔离）',
  user_id        BIGINT       NOT NULL                COMMENT '创建者用户ID',
  name           VARCHAR(64)  NOT NULL                COMMENT 'Agent 名称',
  description    VARCHAR(512) DEFAULT ''              COMMENT '功能描述',
  avatar         VARCHAR(255) DEFAULT ''              COMMENT '图标/头像 URL',
  system_prompt  TEXT         NOT NULL                COMMENT '自定义系统提示词（支持 {context}/{question} 占位符）',
  source_mode    VARCHAR(16)  NOT NULL DEFAULT 'kb'   COMMENT '数据源 kb/content/log',
  kb_id          BIGINT       DEFAULT NULL            COMMENT '绑定知识库（source_mode=kb 时必填）',
  exec_mode      VARCHAR(16)  NOT NULL DEFAULT 'single' COMMENT '执行模型 single单步流式 / multi多步骤流程',
  steps          MEDIUMTEXT   DEFAULT NULL            COMMENT '多步骤流程定义 JSON（exec_mode=multi 时必填）',
  status         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT' COMMENT 'DRAFT/PUBLISHED/ARCHIVED',
  deleted        TINYINT(1)   NOT NULL DEFAULT 0      COMMENT '删除标志',
  create_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_tenant_user (tenant_id, user_id, create_time)
) ENGINE=InnoDB COMMENT='自定义 Agent 定义';

-- 2. 自定义 Agent 运行记录表（一次对话/一次流程执行 = 1 行，审计数据不加软删）
CREATE TABLE IF NOT EXISTS agent_run (
  id            BIGINT       NOT NULL PRIMARY KEY,
  tenant_id     BIGINT       NOT NULL                COMMENT '租户ID',
  user_id       BIGINT       NOT NULL                COMMENT '发起用户ID',
  agent_id      BIGINT       NOT NULL                COMMENT '关联 agent_definition.id',
  session_id    BIGINT       DEFAULT NULL            COMMENT '会话ID（跨轮记忆，同会话多轮 run 共享）',
  input_type    VARCHAR(16)  NOT NULL                COMMENT '输入类型 kb/content/log',
  kb_id         BIGINT       DEFAULT NULL            COMMENT '实际检索知识库ID（input_type=kb）',
  question      VARCHAR(1024) NOT NULL               COMMENT '用户问题/分析诉求',
  context_ref   VARCHAR(512) DEFAULT NULL            COMMENT 'content 原文摘要或 log 文件引用',
  result        MEDIUMTEXT   DEFAULT NULL            COMMENT '最终输出（single=回答；multi=末步报告）',
  steps_result  MEDIUMTEXT   DEFAULT NULL            COMMENT '多步各步输出 JSON（multi 模式）',
  token_usage   INT          DEFAULT 0               COMMENT 'token 总消耗',
  status        VARCHAR(16)  NOT NULL DEFAULT 'CREATED' COMMENT 'CREATED/RUNNING/COMPLETED/FAILED/CANCELED',
  error_msg     VARCHAR(1024) DEFAULT NULL           COMMENT '失败原因',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  finished_time DATETIME     DEFAULT NULL            COMMENT '完成时间',
  KEY idx_tenant_agent (tenant_id, agent_id, create_time),
  KEY idx_tenant_user (tenant_id, user_id, create_time)
) ENGINE=InnoDB COMMENT='自定义 Agent 运行记录';

-- ====================================================================
-- Tool Registry 三表（元数据 / 版本历史 / 权限授权）
-- DB 驱动的工具元数据：补齐工具注册/发现/权限/版本/超时五项能力。
-- 代码层 Tool 接口为可执行行为真源，DB 层为运行时可配置叠加层。
-- 对应实体：com.knowledge.agent.entity.ToolMetadata/ToolVersion/ToolPermission
-- ====================================================================

-- 1) 工具元数据主表：一个 (tenant_id, tool_name) 一行
CREATE TABLE IF NOT EXISTS tool_metadata (
  id              BIGINT       NOT NULL                COMMENT '主键(雪花)',
  tenant_id       BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID(0=平台预置)',
  tool_name       VARCHAR(64)  NOT NULL                COMMENT '工具唯一标识(snake_case，对应 Tool.name())',
  display_name    VARCHAR(128) NOT NULL                COMMENT '展示名称',
  description     VARCHAR(512) NOT NULL DEFAULT ''     COMMENT '工具描述(供 LLM 理解用途)',
  category        VARCHAR(32)  NOT NULL DEFAULT 'other' COMMENT '分类 knowledge/document/communication/http/data/other',
  current_version VARCHAR(32)  NOT NULL DEFAULT '1.0.0' COMMENT '当前生效版本号(指向 tool_version.version)',
  auth_required   TINYINT      NOT NULL DEFAULT 1      COMMENT '是否需KB权限 0否 1是(与代码层 authRequired 镜像)',
  timeout_ms      INT          DEFAULT NULL            COMMENT '单次执行超时毫秒(NULL=用全局默认)',
  enabled         TINYINT      NOT NULL DEFAULT 1      COMMENT '是否启用 0禁用 1启用(租户级开关)',
  icon            VARCHAR(64)  NOT NULL DEFAULT ''     COMMENT '前端展示图标',
  sort_order      INT          NOT NULL DEFAULT 0      COMMENT '排序(升序)',
  creator_id      BIGINT       DEFAULT NULL            COMMENT '创建人ID',
  deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标志 0存在 1删除',
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_tool (tenant_id, tool_name),
  KEY idx_tenant (tenant_id),
  KEY idx_category (category)
) ENGINE=InnoDB COMMENT='工具元数据(同租户内一工具一行)';

-- 2) 工具版本历史表：每个版本一行
CREATE TABLE IF NOT EXISTS tool_version (
  id              BIGINT       NOT NULL                COMMENT '主键(雪花)',
  tenant_id       BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID(0=平台预置)',
  tool_name       VARCHAR(64)  NOT NULL                COMMENT '工具名',
  version         VARCHAR(32)  NOT NULL                COMMENT '版本号(语义化 1.0.0)',
  status          VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT草稿/PUBLISHED已发布/ARCHIVED已归档',
  changelog       VARCHAR(512) NOT NULL DEFAULT ''     COMMENT '版本变更说明',
  schema_snapshot TEXT         DEFAULT NULL            COMMENT '该版本 parametersJsonSchema 快照(审计)',
  creator_id      BIGINT       DEFAULT NULL            COMMENT '创建人ID',
  deleted         TINYINT      NOT NULL DEFAULT 0      COMMENT '删除标志 0存在 1删除',
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_tool_ver (tenant_id, tool_name, version),
  KEY idx_tool_status (tool_name, status)
) ENGINE=InnoDB COMMENT='工具版本历史(同工具多版本)';

-- 3) 工具权限授权表：显式授权/拒绝(无记录=默认开放)
CREATE TABLE IF NOT EXISTS tool_permission (
  id              BIGINT       NOT NULL                COMMENT '主键(雪花)',
  tenant_id       BIGINT       NOT NULL                COMMENT '所属租户ID',
  tool_name       VARCHAR(64)  NOT NULL                COMMENT '工具名',
  subject_type    CHAR(1)      NOT NULL                COMMENT '主体类型 T=租户全局 R=角色级',
  subject_id      BIGINT       NOT NULL                COMMENT '主体ID(T=tenant_id / R=role_id)',
  enabled         TINYINT      NOT NULL DEFAULT 1      COMMENT '授权 0拒绝 1允许',
  creator_id      BIGINT       DEFAULT NULL            COMMENT '创建人ID',
  create_time     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_perm (tenant_id, tool_name, subject_type, subject_id),
  KEY idx_tenant_tool (tenant_id, tool_name)
) ENGINE=InnoDB COMMENT='工具权限授权(无记录=默认开放)';

-- Tool 平台预置种子（tenant_id=0；8 个已注册工具：5 目标 + 3 现有共存）
INSERT INTO tool_metadata (id, tenant_id, tool_name, display_name, description, category, current_version, auth_required, timeout_ms, enabled, icon, sort_order) VALUES
  (4001, 0, 'knowledge_search',  '知识检索',     '在企业知识库中执行混合检索(向量+关键词)，返回权限过滤后的证据片段。', 'knowledge',     '1.0.0', 1, 30000, 1, 'Search',        1),
  (4002, 0, 'document_read',     '文档读取',     '按文档ID获取文档元数据与内容切片，供精确引用具体文档。',           'document',      '1.0.0', 1, 20000, 1, 'Document',      2),
  (4003, 0, 'email_send',        '邮件发送',     '发送邮件(支持 log/smtp 两种模式)，副作用工具需授权。',             'communication', '1.0.0', 0, 30000, 1, 'Message',       3),
  (4004, 0, 'http_request',      'HTTP请求',     '发起 HTTP GET/POST 请求，受白名单管控防 SSRF。',                  'http',          '1.0.0', 0, 20000, 1, 'Link',          4),
  (4005, 0, 'sql_query',         'SQL查询',      '只读 SQL 查询(SELECT/SHOW/DESC/EXPLAIN)，受表白名单+行数限制。',  'data',          '1.0.0', 0, 15000, 1, 'DataAnalysis',  5),
  (4006, 0, 'data_query',        '业务数据查询', '查询知识库与 Agent 任务的统计数据(kb_stats/doc_stats/agent_stats)。', 'data',       '1.0.0', 1, 15000, 1, 'DataLine',      6),
  (4007, 0, 'document_compare',  '文档比较',     '对比两篇文档内容差异，输出结构化对比报告(相同点/差异点/建议)。',   'document',      '1.0.0', 1, 60000, 1, 'DocumentCopy',  7),
  (4008, 0, 'report_generate',   '报告生成',     '基于主题与素材生成结构化报告(标题/摘要/正文/结论)。',             'other',         '1.0.0', 0, 60000, 1, 'EditPen',       8)
ON DUPLICATE KEY UPDATE display_name = VALUES(display_name);

INSERT INTO tool_version (id, tenant_id, tool_name, version, status, changelog, schema_snapshot) VALUES
  (5001, 0, 'knowledge_search', '1.0.0', 'PUBLISHED', '初始版本',
   '{"type":"object","properties":{"query":{"type":"string","description":"检索查询(自然语言关键词)"},"topK":{"type":"integer","description":"返回证据条数","default":8}},"required":["query"]}'),
  (5002, 0, 'document_read', '1.0.0', 'PUBLISHED', '初始版本',
   '{"type":"object","properties":{"documentId":{"type":"integer","description":"文档ID"},"fields":{"type":"string","description":"返回字段 metadata/content/all","default":"all"},"maxChunks":{"type":"integer","description":"内容切片最大条数","default":5}},"required":["documentId"]}'),
  (5003, 0, 'email_send', '1.0.0', 'PUBLISHED', '初始版本',
   '{"type":"object","properties":{"to":{"type":"array","items":{"type":"string"},"description":"收件人邮箱列表"},"subject":{"type":"string","description":"邮件主题"},"content":{"type":"string","description":"邮件正文"},"cc":{"type":"array","items":{"type":"string"},"description":"抄送邮箱列表"}},"required":["to","subject","content"]}'),
  (5004, 0, 'http_request', '1.0.0', 'PUBLISHED', '初始版本',
   '{"type":"object","properties":{"url":{"type":"string","description":"请求URL(须在白名单内)"},"method":{"type":"string","description":"HTTP方法 GET/POST","default":"GET"},"headers":{"type":"object","description":"请求头"},"body":{"type":"string","description":"请求体(POST)"},"timeoutMs":{"type":"integer","description":"超时毫秒"}},"required":["url"]}'),
  (5005, 0, 'sql_query', '1.0.0', 'PUBLISHED', '初始版本',
   '{"type":"object","properties":{"sql":{"type":"string","description":"只读SQL(SELECT/SHOW/DESC/EXPLAIN)"},"params":{"type":"array","description":"预编译参数"},"limit":{"type":"integer","description":"返回行数上限","default":100}},"required":["sql"]}'),
  (5006, 0, 'data_query', '1.0.0', 'PUBLISHED', '初始版本',
   '{"type":"object","properties":{"queryType":{"type":"string","description":"查询类型 kb_stats/doc_stats/agent_stats"},"kbId":{"type":"integer","description":"知识库ID"}},"required":["queryType"]}'),
  (5007, 0, 'document_compare', '1.0.0', 'PUBLISHED', '初始版本',
   '{"type":"object","properties":{"documentId1":{"type":"integer","description":"第一篇文档ID"},"documentId2":{"type":"integer","description":"第二篇文档ID"},"focus":{"type":"string","description":"对比关注点"}},"required":["documentId1","documentId2"]}'),
  (5008, 0, 'report_generate', '1.0.0', 'PUBLISHED', '初始版本',
   '{"type":"object","properties":{"topic":{"type":"string","description":"报告主题"},"content":{"type":"string","description":"报告素材/数据/分析结论"},"format":{"type":"string","description":"输出格式 markdown/text","default":"markdown"}},"required":["topic","content"]}')
ON DUPLICATE KEY UPDATE changelog = VALUES(changelog);
