# worldCoffee — 咖啡社区后端 (Spring Boot 3 + MyBatis-Plus)

Spring Boot 3.4.1 多模块项目，Java 21。咖啡爱好者社区后端，提供帖子、商城、通知推送、私信、AI 问答。

## Project

- **Stack**: Spring Boot 3.4.1, Java 21, MyBatis-Plus 3.5.7, MySQL 8, Redis, RabbitMQ, ElasticSearch 8.10.4, Spring AI (DashScope/Qwen), Spring Security, JWT (jjwt), SpringDoc OpenAPI, Lombok, Hutool
- **Entry point**: `worldcoffee-admin/src/main/java/cn/lx/worldcoffee/WorldCoffeeApplication.java`
- **Modules**:
  - `worldcoffee-common` — 公共模块：JWT 认证、全局异常、Swagger 配置、RabbitMQ 配置
  - `worldcoffee-admin` — 业务模块：用户、帖子、商城、通知、私信、AI

## Commands

| Command | What |
|---|---|
| `./mvnw clean install -DskipTests` | 全量构建（跳过测试） |
| `./mvnw spring-boot:run -pl worldcoffee-admin` | 启动服务（默认 8080） |
| `./mvnw test -pl worldcoffee-admin` | 运行测试 |
| `./mvnw clean package -DskipTests` | 打包可部署 JAR |
| `java -jar worldcoffee-admin/target/*.jar` | 启动打包后的 JAR |

**Docker 依赖**: `docker start mysql8 redis rabbitmq es`

Swagger UI: `http://localhost:8080/swagger-ui/index.html`
RabbitMQ 管理: `http://localhost:15672` (guest/guest)
ES: `http://localhost:9200`

## Architecture

```
worldcoffee-common/              # 公共模块（jar 依赖）
  common.config/                 #   SecurityConfig, WebMvcConfig, OpenApiConfig, RabbitConfig, GlobalExceptionHandler
  common.security/               #   JwtUtil, JwtAuthenticationFilter
  common.redis/                  #   NotificationMessageReceiver (SSE 推送)
  common.result/                 #   Result<T> 统一响应, Constant

worldcoffee-admin/               # 业务模块（可执行 jar）
  WorldCoffeeApplication.java    #   入口 @SpringBootApplication + @MapperScan
  module.user/                   #   用户模块：注册/登录/资料/头像/关注/统计/登出/刷新/注销/批量查/验证码
  module.coffee/                 #   帖子模块：帖子 CRUD、点赞、收藏、评论、搜索、举报、热帖、关注动态
  module.shop/                   #   商城模块：商品(ES搜索)/分类/购物车/订单/收货地址/商品管理
  module.notification/           #   通知模块：RabbitMQ + SSE 实时推送
  module.message/                #   私信模块：RabbitMQ + SSE 即时聊天
  module.ai/                     #   AI 模块：Spring AI + 阿里云 DashScope(Qwen-Plus)
```

**Key flows**:
- JWT 认证：`JwtAuthenticationFilter` 从 `Authorization: Bearer <token>` header 或 `?token=` query 提取 token → 解析 userId 放入 SecurityContext
- 通知/私信推送：业务 `→` RabbitMQ `→` 消费者 `→` SSE `→` 前端
- 商品搜索：MySQL 数据定时同步到 ES，搜索走 ES 不走 MySQL LIKE
- AI 问答：Spring AI + OpenAI 兼容模式接阿里云 DashScope，`ChatClient` 流式/阻塞调用
- 所有 API 返回统一格式 `Result<T> { code, msg, data }`

## Conventions

1. **Package layout**: `module.{name}.{controller,service,dao,domain}` — domain 下分 `from/`（请求体）、`vo/`（响应体）
2. **API 响应**: 全部通过 `Result.success(data)` / `Result.fail(msg)` 返回，code=200 成功，500 失败
3. **异常处理**: `ServiceException`（业务异常） → `Result.fail`；`RuntimeException` → 全局兜底返回 `Result.fail`
4. **依赖注入**: 构造器注入 — `private final Xxx xxx` + `@RequiredArgsConstructor`
5. **DAO**: MyBatis-Plus `BaseMapper<Entity>` 接口 + `@Mapper` 注解；实体用 `@TableName` + `@TableId(type = IdType.AUTO)`
6. **DTO**: 请求体 `from/*` 用 `@Data` + `jakarta.validation` 注解；响应体 `vo/*` 用 `@Data`
7. **JWT 用户获取**: `SecurityContextHolder.getContext().getAuthentication()` → `principal` 存 userId (String)，`details` 存 username
8. **Controller 注释**: 每个方法加 `@Operation(summary=, description=)` 用于 Swagger 文档
9. **图片上传**: 文件存 `upload.path` 指定目录（默认 `D:\mycode\worldCoffee\worldCoffee\uploads\`），通过 `/uploads/**` 静态映射访问
10. **ES 搜索**: `EsProduct` 实体 + `EsProductRepository` + 启动时 `@PostConstruct` 自动同步 MySQL 商品到 ES
11. **Spring AI**: `spring-ai-starter-model-openai` 接 DashScope，`ChatClient` Bean，`model: qwen-plus`

## Notes

<!-- 临时记录、已知问题、待办事项 -->
