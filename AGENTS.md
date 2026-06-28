# worldCoffee — 咖啡社区后端 (Spring Boot 3 + MyBatis-Plus)

Spring Boot 3.4.1 多模块项目，Java 21。咖啡爱好者社区后端，提供帖子（图文/打卡）、商城、通知推送、用户系统。

## Project

- **Stack**: Spring Boot 3.4.1, Java 21, MyBatis-Plus 3.5.7, MySQL 8, Redis, Spring Security, JWT (jjwt), SpringDoc OpenAPI, Lombok, Hutool
- **Entry point**: `worldcoffee-admin/src/main/java/cn/lx/worldcoffee/WorldCoffeeApplication.java`
- **Modules**:
  - `worldcoffee-common` — 公共模块：JWT 认证、全局异常、Swagger 配置、Redis Pub/Sub
  - `worldcoffee-admin` — 业务模块：用户、帖子/评论、商城、通知

## Commands

| Command | What |
|---|---|
| `./mvnw clean install -DskipTests` | 全量构建（跳过测试） |
| `./mvnw spring-boot:run -pl worldcoffee-admin` | 启动服务（默认 8080） |
| `./mvnw test -pl worldcoffee-admin` | 运行测试 |
| `./mvnw clean package -DskipTests` | 打包可部署 JAR |
| `java -jar worldcoffee-admin/target/*.jar` | 启动打包后的 JAR |

Swagger UI: `http://localhost:8080/swagger-ui/index.html`

## Architecture

```
worldcoffee-common/              # 公共模块（jar 依赖）
  common.config/                 #   SecurityConfig, WebMvcConfig, OpenApiConfig, RedisPubSubConfig, GlobalExceptionHandler
  common.security/               #   JwtUtil, JwtAuthenticationFilter
  common.redis/                  #   NotificationMessageReceiver (Redis Pub/Sub 消费者)
  common.result/                 #   Result<T> 统一响应, Constant

worldcoffee-admin/               # 业务模块（可执行 jar）
  WorldCoffeeApplication.java    #   入口 @SpringBootApplication + @MapperScan
  module.user/                   #   用户模块：注册/登录/资料/关注
    controller/                  #     UserController (/api/users)
    service/                     #     UserService
    dao/                         #     UserDao (MyBatis-Plus BaseMapper)
    domain/                      #     User 实体, form/* (请求体), vo/* (响应体)
  module.coffee/                 #   帖子模块：帖子 CRUD、点赞、收藏、评论、搜索、举报
    controller/                  #     CoffeeController (/api/coffee)
    service/                     #     CoffeeService
    dao/                         #     CoffeePostDao, CoffeeLikeDao, CoffeeFavoriteDao, CoffeeCommentDao, ...
    domain/                      #     CoffeePost 等实体, from/*, vo/*
  module.shop/                   #   商城模块：商品、购物车、订单
    controller/                  #     ShopController (/api/shop)
    service/                     #     ShopService
    dao/                         #     CoffeeProductDao, CartItemDao, CoffeeOrderDao, OrderItemDao
    domain/                      #     CoffeeProduct, CartItem, CoffeeOrder, OrderItem, from/*, vo/*
  module.notification/           #   通知模块：SSE 实时推送、通知列表
    controller/                  #     NotificationController (/api/notifications)
    service/                     #     NotificationService (+ impl/RedisPubSubNotificationService)
    dao/                         #     NotificationDao
    domain/                      #     Notification, NotificationEvent, vo/*
```

**Key flows**:
- JWT 认证：`JwtAuthenticationFilter` 从 `Authorization: Bearer <token>` header 或 `?token=` query 提取 token → 解析 userId 放入 SecurityContext
- 通知推送：Redis Pub/Sub (`notify:*` 频道) → `NotificationMessageReceiver` → SSE 推送给前端
- 所有 API 返回统一格式 `Result<T> { code, msg, data }`

## Conventions

1. **Package layout**: `module.{name}.{controller,service,dao,domain}` — domain 下分 `from/`（请求体）、`vo/`（响应体）
2. **API 响应**: 全部通过 `Result.success(data)` / `Result.fail(msg)` 返回，code=200 成功，500 失败
3. **异常处理**: 全局 `@RestControllerAdvice` 捕获 `RuntimeException` → 返回 `Result.fail(e.getMessage())`
4. **依赖注入**: 构造器注入 — `private final Xxx xxx` + `@RequiredArgsConstructor`
5. **DAO**: MyBatis-Plus `BaseMapper<Entity>` 接口 + `@Mapper` 注解；实体用 `@TableName` + `@TableId(type = IdType.AUTO)`
6. **DTO**: 请求体 `from/*` 用 `@Data` + `jakarta.validation` 注解；响应体 `vo/*` 用 `@Data`
7. **JWT 用户获取**: `SecurityContextHolder.getContext().getAuthentication()` → `principal` 存 userId (String)，`details` 存 username
8. **Controller 注释**: 每个方法加 `@Operation(summary=, description=)` 用于 Swagger 文档
9. **图片上传**: 文件存 `uploads/` 目录，通过 `/uploads/**` 静态映射访问
10. **配置**: `application.yaml` 含 JWT secret/expire、MySQL、Redis、MyBatis-Plus 设置

## Notes

<!-- 临时记录、已知问题、待办事项 -->
