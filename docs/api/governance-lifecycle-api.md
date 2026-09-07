# 知识治理生命周期接口文档

> 模块：`knowledge-kb`
> 基路径：`/api/governance`（`server.servlet.context-path=/api`）
> 认证：所有接口需在请求头携带 `Authorization: Bearer <JWT>`，JWT 中 `tenantId` 为多租户隔离权威来源。

---

## 1. 设计概览

在既有治理（去重 / 版本 / 有效期 / 审核 / 质量）之上**非破坏性叠加**生命周期层，统一文档治理状态为 `DRAFT → REVIEW → PUBLISHED → ARCHIVED`。

### 1.1 状态机迁移图

```
                 SUBMIT(requireReview=true)
        ┌──────────────────────────────────┐
        │                                  ▼
     ┌──────┐  PUBLISH(requireReview=false) ┌──────┐  APPROVE  ┌───────────┐  ARCHIVE  ┌──────────┐
     │ DRAFT │ ──────────────────────────▶ │PUBLISHED│◀────────│  REVIEW   │           │ ARCHIVED │
     └──────┘                              └──────────┘         └──────────┘           └──────────┘
        ▲                                       ▲                    │ REJECT                │ RESTORE
        │                                       │                    ▼                       │
        └───────────────────────────────────────┘               ┌──────┐                     │
                          UPDATE(requireReview=false)            │DRAFT │◀────────────────────┘
                          保持 PUBLISHED                          └──────┘
```

| current | action | policy.requireReview | next |
|---|---|:---:|---|
| DRAFT | SUBMIT | true | REVIEW |
| DRAFT | PUBLISH | false | PUBLISHED |
| REVIEW | APPROVE | * | PUBLISHED |
| REVIEW | REJECT | * | DRAFT |
| PUBLISHED | ARCHIVE | * | ARCHIVED |
| ARCHIVED | RESTORE | * | PUBLISHED |
| PUBLISHED | UPDATE | true | REVIEW |
| PUBLISHED | UPDATE | false | PUBLISHED |

其余组合为非法流转，返回业务异常。

### 1.2 与旧 review_status 共存

`lifecycle_status` 与 `review_status`（PENDING/APPROVED/REJECTED）共存，由 `LifecycleBridge` 在每次迁移后同步：

| 目标 lifecycle_status | 同步的 review_status | 同步字段 |
|---|---|---|
| REVIEW | PENDING | 清空 reviewerId / reviewedAt / archivedAt |
| PUBLISHED | APPROVED | reviewerId=操作人, reviewedAt=now, 清空 archivedAt |
| DRAFT（REJECT 到达） | REJECTED | reviewerId=操作人, reviewedAt=now |
| ARCHIVED | 保持 APPROVED | archivedAt=now |

非破坏：旧 `POST /governance/reviews` 接口完全不动；生命周期迁移在 REVIEW 阶段同时写一条 `document_review` 流水，保证旧审核分页仍可见。

### 1.3 检索门禁

| 门禁开关 | 配置项 | 默认 | 作用 |
|---|---|:---:|---|
| 审核门禁 | `kb.governance.review-gate` | false | 仅 APPROVED 文档可被 RAG 检索 |
| 有效期门禁 | `kb.governance.expire-gate` | false | 排除过期/未生效文档 |
| **生命周期门禁** | `kb.governance.lifecycle-gate` | false | 仅 PUBLISHED 文档可被 RAG 检索（排除 DRAFT/REVIEW/ARCHIVED） |

三门禁全关闭时 `filterGovernanceValid` 原样返回；RAG 调用点 `RagServiceImpl` 零改动。

### 1.4 权限模型

| 操作 | 权限要求 |
|---|---|
| 查询生命周期状态 / 策略 / 版本历史 / 审计流水 | viewer 及以上 |
| 生命周期迁移（SUBMIT/PUBLISH/APPROVE/REJECT/ARCHIVE/RESTORE/UPDATE）/ 版本回滚 | editor 及以上 |
| 保存/删除策略 | owner（策略影响整个 KB 治理基线） |

---

## 2. 生命周期接口

### 2.1 查询文档生命周期状态

`GET /governance/lifecycle/{docId}`

**权限**：viewer 及以上

**响应**：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "docId": 1001,
    "kbId": 100,
    "docName": "产品白皮书.pdf",
    "lifecycleStatus": "PUBLISHED",
    "reviewStatus": "APPROVED",
    "version": 3,
    "reviewerId": 9,
    "reviewedAt": "2026-08-02T10:30:00",
    "archivedAt": null,
    "effectiveFrom": null,
    "expireAt": null
  }
}
```

### 2.2 执行生命周期迁移

`POST /governance/lifecycle`

**权限**：editor 及以上

**请求体**：

```json
{
  "docId": 1001,
  "action": "SUBMIT",
  "comment": "提交审核"
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| docId | Long | 是 | 文档ID |
| action | String | 是 | SUBMIT/PUBLISH/APPROVE/REJECT/ARCHIVE/RESTORE/UPDATE |
| comment | String | 否 | 备注（审核意见/归档原因） |

**流程**：checkEditor → 加载文档 → 状态机裁定 → 桥接同步 review_status → 落库 → 写 document_review 流水（REVIEW 阶段）+ 写治理审计。

**非法流转**返回：`code=500, message="非法生命周期流转: DRAFT --APPROVE--> ?"`

---

## 3. 策略接口

每知识库一份策略，驱动生命周期行为。无配置时返回默认策略（`requireReview=true`，其余 null，不落库）。

### 3.1 查询知识库治理策略

`GET /governance/policies/{kbId}`

**权限**：viewer 及以上

**响应**：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "id": null,
    "kbId": 100,
    "requireReview": true,
    "autoArchiveDays": null,
    "retentionDays": null,
    "approverRoles": null,
    "reviewExpireHours": null,
    "createTime": null,
    "updateTime": null
  }
}
```

> `id=null` 表示该 KB 未配置策略，返回内存默认值。

### 3.2 保存或更新策略

`PUT /governance/policies`

**权限**：owner

**请求体**：

```json
{
  "kbId": 100,
  "requireReview": false,
  "autoArchiveDays": 30,
  "retentionDays": 90,
  "approverRoles": "KB_OWNER,KB_EDITOR",
  "reviewExpireHours": 48
}
```

| 字段 | 类型 | 必填 | 说明 |
|---|---|:---:|---|
| kbId | Long | 是 | 知识库ID |
| requireReview | Boolean | 否 | true=须审核后发布；false=可直接发布 |
| autoArchiveDays | Integer | 否 | 发布后自动归档天数，null=不自动归档（须为正数） |
| retentionDays | Integer | 否 | 归档后保留天数，null=永久，达到即硬删除（须为正数） |
| approverRoles | String | 否 | 允许审核角色，逗号分隔 |
| reviewExpireHours | Integer | 否 | 审核超时小时数，null=不超时（须为正数） |

按 `kbId` upsert，记 `POLICY_UPDATE` 审计。

### 3.3 删除策略

`DELETE /governance/policies/{kbId}`

**权限**：owner

删除后该 KB 恢复为默认策略（`requireReview=true`），记 `POLICY_UPDATE` 审计。

---

## 4. 版本回滚接口

### 4.1 版本回滚

`POST /governance/versions/rollback`

**权限**：editor 及以上

**请求体**：

```json
{
  "docId": 1001,
  "version": 1
}
```

**流程**（向前回滚，不删除历史版本，保证版本链可追溯）：

1. 校验目标版本存在，否则抛 `目标版本不存在: 1`
2. 新版本号 = 当前版本 + 1
3. 目标快照 `storedName/filePath/fileSize/md5` 写回主表，`version` 置为新号
4. 插入新 `DocumentVersion`（版本号=新号，避开 `uk_doc_version` 重复）
5. 若 `lifecycleStatus=PUBLISHED` 且 `policy.requireReview=true` → 触发 `UPDATE` 迁移至 REVIEW 重审
6. 记 `VERSION_ROLLBACK` 审计

---

## 5. 审计接口

### 5.1 治理审计流水分页

`GET /governance/audits`

**权限**：viewer 及以上（按 kbId 校验）

**查询参数**：

| 参数 | 类型 | 说明 |
|---|---|---|
| pageNo | Integer | 页码，默认 1 |
| pageSize | Integer | 每页条数，默认 10，上限 100 |
| kbId | Long | 知识库ID |
| docId | Long | 文档ID |
| userId | Long | 操作人ID |
| action | String | 动作：VIEW/DOWNLOAD/EDIT/DELETE/SHARE/SEARCH_HIT/LIFECYCLE_SUBMIT/LIFECYCLE_APPROVE/LIFECYCLE_REJECT/PUBLISH/ARCHIVE/RESTORE/VERSION_ROLLBACK/POLICY_UPDATE/RETENTION_PURGE |
| result | String | A=放行 D=拒绝 |
| beginTime | LocalDateTime | 起始时间 |
| endTime | LocalDateTime | 结束时间 |

**响应**：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": {
    "records": [
      {
        "id": 5001,
        "tenantId": 1,
        "userId": 9,
        "docId": 1001,
        "docName": "产品白皮书.pdf",
        "action": "LIFECYCLE_SUBMIT",
        "result": "A",
        "passReason": "DRAFT->REVIEW:提交审核",
        "denyReason": null,
        "ip": null,
        "userAgent": null,
        "createTime": "2026-08-02T10:30:00"
      }
    ],
    "total": 1,
    "size": 10,
    "current": 1,
    "pages": 1
  }
}
```

> `kb_doc_audit_log` 不走行级租户拦截器，查询时由 Service 从 `TenantContext` 注入 `tenant_id` 显式过滤。

---

## 6. 审计动作词表

| 动作 | 触发点 | result |
|---|---|---|
| LIFECYCLE_SUBMIT | 生命周期 SUBMIT / UPDATE 迁移 | A |
| LIFECYCLE_APPROVE | 生命周期 APPROVE 迁移 | A |
| LIFECYCLE_REJECT | 生命周期 REJECT 迁移 | A |
| PUBLISH | 生命周期 PUBLISH 迁移 | A |
| ARCHIVE | 手动 / 自动归档 | A |
| RESTORE | 恢复归档 | A |
| VERSION_ROLLBACK | 版本回滚 | A |
| POLICY_UPDATE | 策略保存/删除 | A |
| RETENTION_PURGE | 保留期硬删除 | A |
| VIEW/DOWNLOAD/EDIT/DELETE/SHARE/SEARCH_HIT | 既有访问审计（DocPermissionService） | A/D |

---

## 7. 定时任务

| 任务 | cron 配置项 | 默认 | 行为 |
|---|---|---|---|
| 扫描过期文档 | `kb.governance.expire-cron` | `0 0 2 * * ?` | `expire_at` 过期但仍 APPROVED → REJECTED + 审计 |
| **自动归档** | `kb.governance.auto-archive-cron` | `0 30 2 * * ?` | 按每 KB 策略 `autoArchiveDays`，以 `reviewedAt` 为发布时间代理，超期 PUBLISHED → ARCHIVED + 审计 |
| **保留期硬删除** | `kb.governance.retention-cron` | `0 0 3 * * ?` | 按每 KB 策略 `retentionDays`，`archivedAt+retentionDays<now` 的 ARCHIVED 文档物理删 + 级联清理 |

保留期硬删除级联清理表：`document_version` / `document_fingerprint` / `document_quality` / `document_review` / `kb_doc_acl` / `kb_doc_audit_log`，逐条 best-effort（任一失败仅 warn 不回滚已删主表）。

---

## 8. 存量库迁移 SQL

启用 `lifecycle-gate` 前需回填，避免已审核文档被踢出检索：

```sql
-- 1. 增列（新库 init.sql 已含，存量库执行）
ALTER TABLE kb_document ADD COLUMN lifecycle_status VARCHAR(20) DEFAULT 'DRAFT'
    COMMENT '生命周期 DRAFT/REVIEW/PUBLISHED/ARCHIVED' AFTER review_status;
ALTER TABLE kb_document ADD COLUMN archived_at DATETIME DEFAULT NULL
    COMMENT '归档时间(保留期硬删除判定)' AFTER lifecycle_status;

-- 2. 门禁索引
ALTER TABLE kb_document ADD KEY idx_lifecycle (kb_id, lifecycle_status);

-- 3. 回填：已审核通过文档置为 PUBLISHED，其余保持 DRAFT
UPDATE kb_document SET lifecycle_status = 'PUBLISHED'
    WHERE review_status = 'APPROVED' AND deleted = 0;
UPDATE kb_document SET lifecycle_status = 'DRAFT'
    WHERE lifecycle_status = 'DRAFT' AND review_status = 'REJECTED' AND deleted = 0;

-- 4. 策略表（新库 init.sql 已含，存量库执行）
CREATE TABLE IF NOT EXISTS knowledge_policy (
  id                  BIGINT       NOT NULL                COMMENT '主键',
  tenant_id           BIGINT       NOT NULL DEFAULT 0      COMMENT '所属租户ID',
  kb_id               BIGINT       NOT NULL                COMMENT '所属知识库ID',
  require_review      TINYINT(1)   NOT NULL DEFAULT 1      COMMENT '是否强制审核 1=须审核后发布 0=可直接发布',
  auto_archive_days   INT          DEFAULT NULL            COMMENT '发布后自动归档天数 NULL=不自动归档',
  retention_days      INT          DEFAULT NULL            COMMENT '归档后保留天数 NULL=永久 达到即硬删除',
  approver_roles      VARCHAR(256) DEFAULT NULL            COMMENT '允许审核角色 逗号分隔',
  review_expire_hours INT          DEFAULT NULL            COMMENT '审核超时小时数 NULL=不超时',
  create_time         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_kb (tenant_id, kb_id),
  KEY idx_tenant_kb (tenant_id, kb_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '知识治理策略(每KB一份)';
```

---

## 9. 配置项汇总

```yaml
kb:
  governance:
    enabled: true                  # 治理总开关
    simhash-threshold: 3           # 近似重复 Hamming 距离阈值
    duplicate-scope-kb-only: true  # 去重仅同 KB 比对
    review-gate: false             # 检索门禁：仅 APPROVED
    expire-gate: false             # 检索门禁：排除过期
    lifecycle-gate: false          # 检索门禁：仅 PUBLISHED（v3-6 新增）
    schedule-enabled: true         # 定时任务总开关
    expire-cron: "0 0 2 * * ?"     # 扫描过期文档
    auto-archive-cron: "0 30 2 * * ?"  # 自动归档（v3-6 新增）
    retention-cron: "0 0 3 * * ?"      # 保留期硬删除（v3-6 新增）
```
