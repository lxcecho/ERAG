# 贡献指南（Contributing Guide）

欢迎为 **ERAG** 贡献代码、文档或反馈！请先阅读本文，遵守统一的开发规范，让协作更顺畅。

---

## 一、贡献方式

| 方式 | 说明 |
|---|---|
| 🐛 **报告 Bug** | 在 [Issues](https://github.com/) 提交，标题以 `[Bug]` 开头 |
| 💡 **功能建议** | 在 Issues 提交，标题以 `[Feature]` 开头，说明使用场景与期望行为 |
| 📝 **文档改进** | 修正错别字、补充缺失说明、改进示例，直接提交 PR |
| 🧩 **代码贡献** | 按「PR 流程」提交，需通过全部测试 |

> 提交 Issue 时请包含：**运行环境**（OS / JDK / Docker 版本）、**复现步骤**、**期望与实际情况**。

---

## 二、开发环境

| 组件 | 版本要求 |
|---|---|
| JDK | 17+ |
| Maven | 3.8+ |
| Node.js | 18+ |
| Docker | Desktop（用于启动 MySQL / Milvus 等中间件） |
| Ollama | 可选（本地 Embedding：`bge-m3:latest`，1024 维） |
| IDE | IntelliJ IDEA（推荐，安装 **EnvFile** 插件加载 `.env`） |

### 本地启动（简版）

```powershell
# 1. 启动中间件（MySQL 为硬依赖，其余按需）
cd deploy
docker compose up -d mysql milvus elasticsearch rabbitmq redis minio

# 2. 配置环境变量
cp .env.example .env   # 填入 LLM_API_KEY 等真实值

# 3. 启动后端（IDEA 运行 KnowledgeApplication，或命令行）
cd knowledge-backend
mvn install -DskipTests          # 首次：安装依赖模块到本地仓库
mvn -pl knowledge-admin spring-boot:run

# 4. 启动前端
cd knowledge-frontend
npm install
npm run dev
```

> 详细步骤见 [docs/本地部署调试手册.md](docs/本地部署调试手册.md) 与 [docs/IDEA启动指南.md](docs/IDEA启动指南.md)。

---

## 三、代码规范

### 3.1 分支命名

| 类型 | 前缀 | 示例 |
|---|---|---|
| 新功能 | `feat/` | `feat/model-router-openai` |
| 修复 | `fix/` | `fix/rerank-timeout` |
| 重构 | `refactor/` | `refactor/agent-caller` |
| 文档 | `docs/` | `docs/api-examples` |

### 3.2 提交信息（Conventional Commits）

```
<type>(<scope>): <subject>

<可选：说明修改原因与影响>
```

- **type**：`feat` / `fix` / `docs` / `refactor` / `test` / `chore` / `perf`
- **scope**：模块名，如 `agent` / `rag` / `kb` / `workflow` / `frontend` / `deploy`
- **示例**：`fix(rag): 修复 RRF 融合权重未归一化导致的排序异常`

### 3.3 语言与风格

- 后端 Java 17，遵循 Spring Boot 分层规范（Controller → Service → Mapper），类注释使用 Javadoc
- 前端 Vue3 + TypeScript + Element Plus，组件按 `src/views` / `src/components` 组织
- **注释与文档默认使用中文**（与现有代码一致）；对外 API 命名使用英文
- 命名风格：Java `camelCase`、常量 `UPPER_SNAKE`；TS 变量 `camelCase`、组件 `PascalCase`

### 3.4 工程约束

- **AI 框架统一使用 LangChain4j**（RAG 层与 Agent 层共用 `AiConfig` 创建的 `ChatModel`），禁止引入其他 LLM 框架
- 所有 LLM 调用必须通过 `AgentLlmCaller` 封装（埋点 + 熔断 + 健康上报），禁止散落裸调用
- 新增配置项需同步更新 [deploy/.env.example](deploy/.env.example) 与对应文档
- 数据库变更需同步更新 [deploy/init.sql](deploy/init.sql) 与 `knowledge-backend/doc/sql/` 下对应脚本
- 多租户场景的异步线程必须显式传递 `TenantContext` / `LlmIdentity`，禁止依赖 `InheritableThreadLocal`

---

## 四、测试要求

提交 PR 前必须通过：

```powershell
# 后端：全量单测（项目现有 350+ 用例）
cd knowledge-backend
mvn test

# 前端：类型检查 + 构建
cd knowledge-frontend
npm run type-check
npm run build
```

> 新增功能建议补充单元测试；涉及多步逻辑（RAG 流水线 / Agent 引擎 / Workflow / 状态机）的模块尤其需要。

---

## 五、PR 流程

1. **Fork** 本仓库并克隆到本地
2. **新建分支**：从 `main` 切出，命名遵循上文分支规范
3. **开发 + 自测**：本地启动调试（见第二章），确保功能可用、测试通过
4. **提交**：按 Conventional Commits 规范提交，信息清晰描述「改了什么、为什么」
5. **推送并提 PR**：
   - PR 标题：`<type>(<scope>): <subject>`，正文说明修改动机、变更点、自测结果
   - 关联相关 Issue（如 `Closes #123`）
6. **CI 检查**：等待仓库 CI 通过（后端单测 + 前端构建）
7. **评审**：维护者评审后合并；如要求修改，请在**同一分支**继续提交并推送

### PR Checklist（提交前自查）

- [ ] 代码风格与现有代码一致
- [ ] 本地 `mvn test` 全部通过
- [ ] 前端 `npm run build` 通过
- [ ] 新增/变更配置已同步 `.env.example` 与文档
- [ ] 提交信息遵循 Conventional Commits
- [ ] 已关联相关 Issue

---

## 六、行为准则

- 尊重所有参与者，友善沟通，就事论事
- 评审意见针对**代码**而非个人；给出建议时可附带理由或示例
- 禁止在 PR/Issue 中泄露任何 API Key、密钥或敏感信息

---

## 七、License

提交代码即视为同意以 **Apache License 2.0**（见 [LICENSE](LICENSE)）授权你的贡献。贡献者保留版权，详见许可文本。
