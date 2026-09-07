# 账户信息模块接口文档

> 模块：`knowledge-auth`
> 基路径：`/api/auth`（`server.servlet.context-path=/api`）
> 认证：除 `login` 外均需 `Authorization: Bearer <JWT>`。
> 状态：已通过前后端联调脚本验证（PASS=22/22）。

---

## 1. 接口列表

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/auth/info` | 当前用户信息 |
| PUT | `/auth/profile` | 更新个人资料 |
| PUT | `/auth/password` | 修改密码 |
| POST | `/auth/avatar` | 上传头像 |
| GET | `/auth/my-tasks` | 我的任务聚合（Agent 任务 + 自定义 Agent 运行） |

> 注意：`/profile` 与 `/password` 均为 **PUT**；`/avatar` 为 **POST**（multipart）。

---

## 2. 当前用户信息

```
GET /auth/info
```

响应 `UserInfoVo`：

```json
{
  "id": 1,
  "tenantId": 1,
  "username": "admin",
  "nickname": "联调管理员",
  "avatar": "/avatars/4e2dd728....png",
  "email": "admin@erag.local",
  "phone": "13800138000",
  "roles": ["admin"],
  "permissions": ["system:user:list"]
}
```

`avatar` 为 `/avatars/**` 相对路径，由 `WebMvcConfig` 静态资源映射到 `{user.dir}/data/avatars/` 提供访问。

---

## 3. 更新个人资料

```
PUT /auth/profile
Content-Type: application/json
```

请求体（`ProfileRequest`，字段均可选）：

```json
{ "nickname": "联调管理员", "email": "admin@erag.local", "phone": "13800138000" }
```

| 字段 | 校验 |
|------|------|
| `nickname` | ≤64 字符 |
| `avatar` | ≤255 字符（URL 或 /avatars/ 路径） |
| `email` | 邮箱格式 |
| `phone` | 空 或 `1[3-9]\d{9}` |

响应：更新后的 `UserInfoVo`。

---

## 4. 修改密码

```
PUT /auth/password
Content-Type: application/json
```

请求体（`PasswordRequest`）：

```json
{ "oldPassword": "123456", "newPassword": "abc12345" }
```

| 字段 | 校验 |
|------|------|
| `oldPassword` | 必填，须与当前密码一致（不一致返回业务异常） |
| `newPassword` | 6-64 位 |

响应：`Result<Void>`。修改成功后旧 token 不受影响。

---

## 5. 上传头像

```
POST /auth/avatar
Content-Type: multipart/form-data
参数：file（单文件）
```

| 限制 | 值 |
|------|------|
| 大小 | ≤ 2MB |
| 类型 | png / jpg / jpeg / webp / gif |

响应 `data`：

```json
"/avatars/4e2dd728b0af4c9ca1b6c65bf93afe92.png"
```

> 存储：`{user.dir}/data/avatars/`（**基于 user.dir 的绝对路径**，避免 `transferTo` 相对路径被解析到 Tomcat 临时目录导致 FileNotFoundException）。
> 覆盖上传时旧头像 best-effort 删除。

---

## 6. 我的任务聚合

```
GET /auth/my-tasks?pageNum=1&pageSize=10
```

| 参数 | 默认 | 说明 |
|------|:---:|------|
| `pageNum` | 1 | 页码 |
| `pageSize` | 10 | 每页条数 |

响应：分页的 `MyTaskVo` 列表（统一聚合 `agent_task` 与 `agent_run`，按创建时间倒序）：

```json
{
  "records": [
    {
      "taskType": "CUSTOM_AGENT",
      "taskId": "2083981332129132545",
      "agentId": "2083981332129132545",
      "agentName": "日志分析助手",
      "title": "日志中数据库连接超时的原因是什么？",
      "status": "COMPLETED",
      "tokenUsage": 352,
      "errorMsg": null,
      "createTime": "2026-08-03 02:00:00",
      "finishedTime": "2026-08-03 02:00:30"
    }
  ],
  "total": 1
}
```

| 字段 | 说明 |
|------|------|
| `taskType` | `AGENT`（自主式 Agent 任务）/ `CUSTOM_AGENT`（自定义 Agent 运行） |
| `taskId` | agent_task.id 或 agent_run.id（字符串） |
| `agentId` / `agentName` | 自定义 Agent 定义（CUSTOM_AGENT 时） |
| `title` | 展示标题（goal / question） |
| `status` | CREATED/RUNNING/COMPLETED/FAILED/CANCELED |
