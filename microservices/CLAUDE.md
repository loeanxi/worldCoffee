# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 构建与运行

```bash
# 父工程聚合构建（跳过测试）
mvn clean package -DskipTests

# 单服务构建
mvn clean package -DskipTests -pl wc-shop

# 运行单服务（以 wc-shop 为例）
java -jar wc-shop/target/wc-shop-0.0.1-SNAPSHOT-exec.jar
```

> 所有服务依赖本地启动的 **Nacos（localhost:8848）、MySQL（localhost:3306/worldCoffee）、Redis（localhost:6379）、RabbitMQ（localhost:5672）**。
> wc-shop 额外依赖 **Elasticsearch**（Product 全文搜索）和 **Redisson**（分布式锁）。
> wc-ai 额外依赖 **Spring AI** 配置（ChatClient / VectorStore），缺少时会降级返回提示信息。

## 模块职责

| 模块 | 端口 | 职责 |
|------|------|------|
| `wc-gateway` | 8080 | 统一入口：JWT 鉴权（WebFlux GatewayFilter）、路由转发、CORS、TraceId 注入 |
| `wc-common` | — | 共享库：`Result<T>`、`ServiceException`、JWT 工具、RabbitMQ 拓扑定义、Redis 限流拦截器、MinIO 文件上传 |
| `wc-user` | — | 用户注册/登录/资料、关注关系、用户主页聚合 |
| `wc-shop` | 8081 | 商品/分类/购物车/订单/支付（Mock）/优惠券/秒杀/物流/ES 搜索 |
| `wc-community` | — | 帖子/评论/点赞/收藏/话题/举报/Feed 流 |
| `wc-message` | — | 系统通知（SSE 推送）+ 私信（RabbitMQ + SSE） |
| `wc-ai` | — | AI 对话（Spring AI ChatClient + 对话记忆）、知识库向量存储 |
| `wc-admin` | — | 管理后台：用户管理、商品管理、内容审核、敏感词、操作日志 |

## 架构关键点

### 认证传递
网关 `JwtAuthFilter` 解析 JWT → 写入 `X-User-Id` 请求头 → 下游服务通过 `SecurityUtils.requireUserId()` / `request.getHeader("X-User-Id")` 取出，**不依赖 Spring Security Context**。

### 路由规则（wc-gateway/application.yml）
```
/api/admin/**    → wc-admin
/api/shop/**     → wc-shop
/api/coffee/**   → wc-community
/api/user/**     → wc-user
/api/users/**    → wc-user
/api/notifications/** → wc-message
/api/messages/** → wc-message
/api/ai/**       → wc-ai
/uploads/**      → wc-user（静态文件）
```

### RabbitMQ 拓扑（定义在 `wc-common` `RabbitConfig`）
| 用途 | Exchange | Queue |
|------|----------|-------|
| 系统通知 | `notification.exchange`（Topic） | `notification.queue` |
| 私信 | `chat.exchange`（Topic） | `chat.queue.default` |
| 秒杀下单 | `seckill.order.exchange` | `seckill.order.queue`（配死信 → `seckill.order.dead.queue`） |
| 订单超时 | `order.timeout.exchange` | `order.timeout.delay.queue`（TTL 15 min → `order.timeout.queue`） |

**所有 Exchange / Queue 声明统一放在 `wc-common`，各消费服务直接注入 `RabbitTemplate` 即可。**

### 秒杀流程
1. Redis 预扣秒杀券库存（`seckill:stock:{couponId}`）
2. 唯一索引防重（`user_coupon` 表）
3. 写 `seckill_event`（本地消息表）→ 发 MQ → 更新事件状态
4. `SeckillOrderConsumer` 消费 MQ 异步建单、扣商品库存
5. 失败进死信队列由 `SeckillOrderDeadLetterConsumer` 兜底补偿
6. 限流：`RateLimitInterceptor`（Redis Lua 滑动窗口）仅拦截 `/seckill/buy`，全局 100 req/s + 用户 5 次/min + IP 10 次/min

### 服务间调用
跨服务数据读取使用 **OpenFeign**（如 `wc-message` 调 `wc-user` 获取用户信息），Feign 客户端放在各服务 `feign` 子包。`wc-user` 模块内存在部分冗余实体（`CoffeePost`、`CoffeeLike` 等）供聚合查询，非业务主库。

### wc-common 自动装配
`wc-common` 作为依赖被其他服务引入，以下 Bean 会自动生效：
- `GlobalExceptionHandler`（`@RestControllerAdvice`）
- `TraceIdFilter`（MDC TraceId）
- `RateLimitInterceptor`（`WebMvcConfig` 注册，仅拦截秒杀接口）
- `FileStorageService` + MinIO 配置

`wc-gateway` 排除了 `spring-boot-starter-web`（使用 WebFlux），因此上述 MVC Bean 在网关不生效。

## 包结构约定

- 业务服务：`cn.lx.worldcoffee.<模块名>.module.<领域>.[controller|service|dao|domain]`
- `domain` 下细分：`from`（入参 Form）、`vo`（出参 VO）、`message`（MQ 消息体）
- 公共模块：`cn.lx.worldcoffee.common.[result|exception|security|config|storage]`
