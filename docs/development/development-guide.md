# ERAG 开发指南

> 本文档涵盖 ERAG 系统的开发环境搭建、代码规范、测试策略和贡献流程。

---

## 一、开发环境

### 1.1 环境要求

| 工具 | 版本 | 说明 |
|------|------|------|
| JDK | 17+ | 推荐 Eclipse Temurin |
| Maven | 3.9+ | 构建工具 |
| Node.js | 20+ | 前端构建 |
| Docker | 24+ | 中间件运行 |
| IDE | IntelliJ IDEA | 推荐，已有配置 |

### 1.2 快速启动

#### 方式一：Docker 中间件 + IDE 后端/前端

```bash
# 1. 启动中间件
docker compose -f docker-compose.dev.yml up -d

# 2. 后端（IDEA 直接运行 KnowledgeApplication）
# 配置：application-dev.yml 指向 Docker 中间件

# 3. 前端
cd knowledge-frontend
npm install
npm run dev
```

#### 方式二：全 Docker 启动

```bash
# 一键启动
docker compose up -d --build

# 访问 http://localhost
```

### 1.3 配置文件

```
knowledge-backend/knowledge-admin/src/main/resources/
├── application.yml          # 公共配置
├── application-dev.yml      # 开发环境（数据库连接）
└── db/migration/            # Flyway 迁移脚本
```

#### 关键配置

```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/knowledge_ai
    username: root
    password: root123

ai:
  llm:
    base-url: http://localhost:11434/v1  # Ollama
    model-name: deepseek-chat
  embedding:
    base-url: http://localhost:11434/v1
    model-name: bge-m3
    batch-size: 64  # 批量大小，可按需调整
```

---

## 二、项目结构

### 2.1 后端模块

```
knowledge-backend/
├── knowledge-common/    # 公共模块（异常/返回/租户/MQ/告警）
├── knowledge-infra/     # 基础设施（MyBatis-Plus 配置）
├── knowledge-system/    # 系统管理（用户/角色/菜单/租户）
├── knowledge-auth/      # 认证授权（JWT/Security）
├── knowledge-kb/        # 知识库（文档/成员/治理/存储）
├── knowledge-ai/        # AI 核心（RAG/检索/Prompt/运维）
├── knowledge-agent/     # Agent 引擎（Agent/Workflow/Tool/记忆）
└── knowledge-admin/     # 启动层（聚合配置/主类）
```

#### 模块依赖

```
common → infra → system → auth → kb → ai → agent → admin
```

### 2.2 前端结构

```
knowledge-frontend/
├── src/
│   ├── api/          # API 接口
│   │   ├── request.ts    # Axios 封装（自动刷新 token）
│   │   ├── auth.ts       # 认证接口
│   │   ├── chat.ts       # 聊天接口
│   │   └── ...
│   ├── stores/       # Pinia 状态管理
│   │   └── user.ts       # 用户状态
│   ├── router/       # 路由配置
│   ├── utils/        # 工具函数
│   │   ├── sse.ts        # SSE 流式（带断线重连）
│   │   └── auth.ts       # Token 管理（双 token）
│   ├── views/        # 页面组件
│   └── types/        # TypeScript 类型定义
├── package.json
└── vite.config.ts
```

---

## 三、代码规范

### 3.1 Java 代码规范

#### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 类名 | PascalCase | `RagServiceImpl` |
| 方法名 | camelCase | `hybridSearch` |
| 常量 | UPPER_SNAKE | `EMBED_BATCH_SIZE` |
| 包名 | 小写 | `com.knowledge.ai` |

#### 代码格式

使用 Spotless 自动格式化：

```bash
# 检查格式
mvn spotless:check

# 自动修复
mvn spotless:apply
```

配置：Google Java Format (AOSP 风格)

#### 注释规范

```java
/**
 * 服务实现：简要说明。
 * <p>详细说明设计原因和使用场景。
 * <p>注意事项：
 * <ul>
 *   <li>xxx</li>
 * </ul>
 *
 * @author: lxcechoo@gmail.com
 */
@Service
public class XxxServiceImpl implements XxxService {
    
    /** 字段说明 */
    private final Xxx xxx;
    
    /**
     * 方法说明。
     *
     * @param param1 参数1说明
     * @return 返回值说明
     */
    @Override
    public Result method(String param1) {
        // 逻辑说明
    }
}
```

### 3.2 TypeScript 代码规范

#### 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 组件名 | PascalCase | `ChatIndex.vue` |
| 文件名 | kebab-case | `chat-index.vue` |
| 变量/函数 | camelCase | `getToken` |
| 类型/接口 | PascalCase | `UserInfo` |
| 常量 | UPPER_SNAKE | `TOKEN_KEY` |

#### Vue 组件规范

```vue
<script setup lang="ts">
// 1. 导入
import { ref, onMounted } from 'vue'
import { useUserStore } from '@/stores/user'

// 2. 类型定义
interface Props {
  title: string
  count?: number
}

// 3. Props/Emits
const props = withDefaults(defineProps<Props>(), {
  count: 0
})

const emit = defineEmits<{
  (e: 'update', value: string): void
}>()

// 4. 状态
const userStore = useUserStore()
const loading = ref(false)

// 5. 方法
function handleClick() {
  emit('update', 'new value')
}

// 6. 生命周期
onMounted(() => {
  // 初始化逻辑
})
</script>
```

---

## 四、测试策略

### 4.1 测试类型

| 类型 | 工具 | 覆盖率目标 | 说明 |
|------|------|-----------|------|
| 单元测试 | JUnit 5 + Mockito | 70%+ | 纯逻辑测试，不启动 Spring |
| 集成测试 | Testcontainers | 50%+ | 启动容器，测试完整流程 |
| E2E 测试 | Playwright | 关键路径 | 前后端联调 |

### 4.2 单元测试

#### 编写规范

```java
@ExtendWith(MockitoExtension.class)
class RagServiceImplTest {

    @Mock
    private EmbeddingService embeddingService;
    
    @Mock
    private MilvusService milvusService;
    
    @InjectMocks
    private RagServiceImpl ragService;

    @Test
    @DisplayName("无命中资料时返回暂无信息")
    void ask_noResults_returnsNoInfo() {
        // Given
        when(embeddingService.embed(any())).thenReturn(mockEmbedding);
        when(milvusService.search(any(), anyLong(), anyInt())).thenReturn(List.of());
        
        // When
        ChatResult result = ragService.ask("测试问题", 1L, null);
        
        // Then
        assertThat(result.answer()).contains("暂无相关信息");
    }
}
```

#### 运行测试

```bash
# 运行所有单元测试
mvn test

# 运行指定模块测试
mvn test -pl knowledge-ai

# 运行指定测试类
mvn test -Dtest=RagServiceImplTest

# 生成覆盖率报告
mvn test jacoco:report
```

### 4.3 集成测试

#### 使用 Testcontainers

```java
@SpringBootTest
@Testcontainers
class UserServiceIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0.33")
            .withDatabaseName("knowledge_ai_test")
            .withInitScript("db/init-test.sql");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
    }

    @Autowired
    private UserService userService;

    @Test
    void createUser_success() {
        // 测试代码
    }
}
```

### 4.4 测试覆盖率

```bash
# 生成覆盖率报告
mvn test jacoco:report

# 查看报告
open knowledge-backend/target/site/jacoco/index.html

# 覆盖率门禁（最低 50%）
mvn test jacoco:check
```

---

## 五、Git 工作流

### 5.1 分支策略

```
main (生产)
  │
  ├── develop (开发)
  │     │
  │     ├── feature/xxx (功能分支)
  │     └── bugfix/xxx (修复分支)
  │
  └── release/v1.0.0 (发布分支)
```

### 5.2 提交规范

```
<type>(<scope>): <subject>

<body>

<footer>
```

#### Type 类型

| 类型 | 说明 |
|------|------|
| feat | 新功能 |
| fix | Bug 修复 |
| docs | 文档更新 |
| style | 代码格式（不影响逻辑） |
| refactor | 重构 |
| perf | 性能优化 |
| test | 测试 |
| chore | 构建/工具 |

#### 示例

```
feat(ai): 添加加权 RRF 融合支持

- 新增 rrf(vectorResults, keywordResults, k, vectorWeight, keywordWeight) 方法
- 支持根据查询特征动态调整向量/BM25 权重
- 更新 ResultFusion 单元测试

Closes #123
```

### 5.3 Pull Request 流程

1. 从 `develop` 创建功能分支
2. 开发完成后提交 PR
3. 代码审查（至少 1 人 approve）
4. CI 流水线通过
5. 合并到 `develop`

---

## 六、新增功能指南

### 6.1 新增 API 接口

#### 1. 创建 Controller

```java
@RestController
@RequestMapping("/xxx")
@RequiredArgsConstructor
public class XxxController {

    private final XxxService xxxService;

    @Operation(summary = "xxx 接口")
    @GetMapping("/list")
    public Result<List<XxxVo>> list() {
        return Result.success(xxxService.list());
    }
}
```

#### 2. 创建 Service

```java
public interface XxxService {
    List<XxxVo> list();
}

@Service
@RequiredArgsConstructor
public class XxxServiceImpl implements XxxService {
    
    private final XxxMapper xxxMapper;
    
    @Override
    public List<XxxVo> list() {
        return xxxMapper.selectList().stream()
            .map(this::toVo)
            .toList();
    }
}
```

#### 3. 前端调用

```typescript
// api/xxx.ts
import { request } from '@/api/request'

export function listXxxApi(): Promise<ApiResponse<XxxVo[]>> {
  return request<XxxVo[]>({ url: '/xxx/list', method: 'get' })
}
```

### 6.2 新增 Agent 工具

#### 1. 实现 Tool 接口

```java
@Component
public class XxxTool implements Tool {

    @Override
    public String name() {
        return "xxx_tool";
    }

    @Override
    public String description() {
        return "xxx 工具描述";
    }

    @Override
    public String parametersJsonSchema() {
        return """
            {
                "type": "object",
                "properties": {
                    "param1": {"type": "string", "description": "参数1"}
                },
                "required": ["param1"]
            }
            """;
    }

    @Override
    public boolean authRequired() {
        return false;
    }

    @Override
    public String execute(ToolContext ctx, Map<String, Object> params) {
        String param1 = (String) params.get("param1");
        // 执行逻辑
        return "执行结果";
    }
}
```

#### 2. 注册到 ToolRegistry

工具会通过 `@Component` 自动注册到 `ToolRegistry`，无需额外配置。

### 6.3 新增数据库表

#### 1. 创建实体类

```java
@Data
@TableName("xxx_table")
public class XxxTable {
    
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long tenantId;
    
    private String name;
    
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
```

#### 2. 创建 Mapper

```java
@Mapper
public interface XxxTableMapper extends BaseMapper<XxxTable> {
}
```

#### 3. 创建 Flyway 迁移

```sql
-- V3__add_xxx_table.sql
CREATE TABLE xxx_table (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    tenant_id BIGINT DEFAULT 1,
    name VARCHAR(128) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

---

## 七、调试技巧

### 7.1 后端调试

#### 远程调试

```bash
# docker-compose.yml 环境变量
JAVA_OPTS: >-
  -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005

# IDEA 远程调试配置
# Host: localhost
# Port: 5005
```

#### SQL 日志

```yaml
# application-dev.yml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

#### Sentinel 日志

```yaml
# application-dev.yml
logging:
  level:
    com.alibaba.csp.sentinel: DEBUG
```

### 7.2 前端调试

#### Vue Devtools

```bash
# 安装浏览器扩展
# Chrome: https://chrome.google.com/webstore/detail/vuejs-devtools/nhdogjmejiglipccpnnnanhbledajbpd
```

#### API 调试

```typescript
// 在浏览器控制台
fetch('/api/xxx/list')
  .then(r => r.json())
  .then(console.log)
```

### 7.3 数据库调试

```bash
# 进入 MySQL 容器
docker compose exec mysql mysql -uroot -proot123 knowledge_ai

# 查看慢查询
SHOW VARIABLES LIKE 'slow_query_log';
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1;

# 查看执行计划
EXPLAIN SELECT * FROM chat_message WHERE session_id = 1;
```

---

## 八、CI/CD

### 8.1 GitHub Actions

项目已配置 CI/CD 流水线：

```yaml
# .github/workflows/ci.yml
name: CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]

jobs:
  backend:
    # 后端构建 + 测试
    
  frontend:
    # 前端构建 + 类型检查
    
  docker:
    # Docker 构建 + 推送（仅 main 分支）
    
  security:
    # 安全扫描
```

### 8.2 本地 CI 验证

```bash
# 后端构建
cd knowledge-backend
mvn clean compile -B -DskipTests
mvn test -B

# 前端构建
cd knowledge-frontend
npm ci
npx vue-tsc --noEmit
npm run build
```

---

## 九、常见开发问题

### Q1: Maven 依赖下载慢

```xml
<!-- settings.xml 添加阿里云镜像 -->
<mirrors>
    <mirror>
        <id>aliyun</id>
        <mirrorOf>central</mirrorOf>
        <url>https://maven.aliyun.com/repository/public</url>
    </mirror>
</mirrors>
```

### Q2: 前端 npm install 失败

```bash
# 清理缓存
npm cache clean --force

# 使用淘宝镜像
npm config set registry https://registry.npmmirror.com

# 重新安装
rm -rf node_modules package-lock.json
npm install
```

### Q3: 数据库迁移失败

```bash
# 查看 Flyway 状态
mvn flyway:info

# 修复失败的迁移
mvn flyway:repair

# 手动执行迁移
mvn flyway:migrate
```

### Q4: 测试容器启动失败

```bash
# 检查 Docker 是否运行
docker info

# 检查镜像是否存在
docker images | grep mysql

# 手动拉取镜像
docker pull mysql:8.0.33
```

---

> 📌 返回：[架构文档](../architecture.md) | [部署文档](../../deploy/deploy.md)
