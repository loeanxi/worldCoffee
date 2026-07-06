# worldCoffee 项目审查报告

> 审查时间：2026-07-06  
> 项目结构：Spring Boot 3.4.1 (Java 21) 后端 + Vue 3 用户前端 + Vue 3 管理后台前端

---

## 一、严重（安全漏洞）— 必须立即修复

### 1. API Key 明文泄露并已提交到 Git
- **文件**：`backend/worldcoffee-admin/src/main/resources/application.yaml` 第 44 行
- **问题**：阿里云 DashScope API Key `sk-ws-...` 硬编码在配置文件中，且该文件已被 Git 跟踪并提交。
- **风险**：任何能访问仓库的人都能拿到 Key，可被盗用产生费用。
- **修复**：
  1. 立即到阿里云控制台吊销该 Key，重新生成。
  2. 将敏感配置移到 `application-local.yaml`（加入 `.gitignore`）或环境变量。
  3. 用 `git filter-branch` 或 BFG 清除 Git 历史中的 Key 痕迹。

### 2. 数据库密码弱且明文
- **文件**：`application.yaml` 第 17 行 `password: 123456`
- **修复**：同上，移到环境变量 / local 配置。

### 3. 管理员凭据明文存储 + 明文比对
- **文件**：`application.yaml` 第 96-98 行 `admin/admin123`；`AdminAuthService.java` 第 49 行用 `equals()` 明文比对密码。
- **问题**：项目已配置了 `BCryptPasswordEncoder` Bean（`SecurityConfig.java`），但管理员登录完全没用上。
- **修复**：密码改为 BCrypt 哈希存储，登录时用 `passwordEncoder.matches()`。

### 4. JWT Secret 可预测
- **文件**：`application.yaml` 第 66 行
- **问题**：密钥是 `REDACTED_JWT_SECRET`，完全可预测，攻击者可伪造任意用户/管理员的 token。
- **修复**：使用随机生成的 256 位密钥，存到环境变量。

### 5. 上传路径硬编码绝对路径
- **文件**：`application.yaml` 第 70 行 `upload.path: D:\mycode\worldCoffee\worldCoffee\uploads`
- **问题**：绑死本机路径，换机器或部署即崩溃。
- **修复**：改为相对路径 `./uploads` 或环境变量。

---

## 二、高（工程规范 / Git 卫生）

### 6. admin-frontend 整个目录未纳入版本控制
- `git status` 显示 `?? admin-frontend/`，管理后台前端代码完全未提交。
- **修复**：`git add admin-frontend/` 并提交（排除 node_modules）。

### 7. .gitignore 不完整且有重复
- **缺失**：`admin-frontend/node_modules/`、`admin-frontend/dist/`、`backend/node_modules/`、`.workbuddy/`、`backend/.reasonrix/`
- **重复**：`.idea/` 出现 3 次（第 1、2、5 行）
- **修复**：重写 `.gitignore`（见报告末尾建议模板）。

### 8. 一次性脚本遗留仓库
- **文件**：`backend/add-coupon-columns.js`
- **问题**：数据库迁移脚本，依赖 `backend/node_modules/`（MySQL2），不应留在 Java 项目里。
- **修复**：删除该脚本和 `backend/node_modules/`；迁移改用 Flyway/Liquibase 或 SQL 文件。

### 9. 大量改动未提交
- 23 个文件已修改未提交，包括核心安全文件（SecurityConfig、JwtUtil、JwtAuthenticationFilter）。
- 涉及 ShopService 拆分（删除 902 行）、新增管理后台模块、优惠券/秒杀/物流等功能。
- **建议**：尽快分批提交，避免代码丢失。

### 10. Maven Wrapper 损坏
- `./mvnw` 报 `ClassNotFoundException: org.codehaus.plexus.classworlds.launcher.Launcher`，wrapper jar 缺失或损坏。
- **修复**：重新生成 wrapper：`mvn wrapper:wrapper`。

---

## 三、中（功能 Bug）

### 11. admin-frontend 响应拦截器字段名错误（确认 Bug）
- **文件**：`admin-frontend/src/utils/request.js` 第 29、38 行
- **问题**：后端 `Result` 类字段是 `msg`（见 `Result.java` 第 8 行），但 admin-frontend 用了 `res.message`：
  ```js
  // 第29行 — 错误：res.message 永远 undefined
  ElMessage.error(res.message || '请求失败')
  // 第38行 — 同样错误
  ElMessage.error(error.response?.data?.message || '网络错误')
  ```
- **后果**：管理后台所有业务错误提示永远显示"请求失败"/"网络错误"，看不到后端返回的真实原因。
- **修复**：`res.message` → `res.msg`，`data?.message` → `data?.msg`。

### 12. admin-frontend 缺少 Vite 代理配置
- **文件**：`admin-frontend/vite.config.js`
- **问题**：未配置 `server.proxy`，`request.js` 直接用 `baseURL: 'http://localhost:8080'`。虽然后端 CORS 已放开能工作，但生产部署需额外处理跨域，且不如代理方式统一。
- **建议**：参照 `frontend/vite.config.js` 添加 proxy，`baseURL` 改为 `/api`。

### 13. 分页 SQL 字符串拼接
- **文件**：`AdminService.java` 第 64、110、169 行
- **问题**：`.last("LIMIT " + (page - 1) * size + "," + size)`，`page` 为 0 或负数时会产生非法 SQL。
- **修复**：使用 MyBatis-Plus 的 `Page<T>` 对象分页，自动处理边界。

### 14. admin-frontend 路由缺少 404 兜底
- **文件**：`admin-frontend/src/router/index.js`
- **问题**：没有 catch-all 路由，访问未定义路径会白屏。
- **修复**：添加 `{ path: '/:pathMatch(.*)*', redirect: '/dashboard' }`。

---

## 四、低（代码质量）

### 15. admin-frontend 残留脚手架模板文件
- `src/components/HelloWorld.vue`、`src/assets/vue.svg`、`src/assets/vite.svg` 是 Vite 默认模板，应删除。

### 16. Constant 类设计不当
- **文件**：`Constant.java`
- **问题**：`LOGIN_KEY` 是实例字段（非 `static`），且 `{userId}` 是字面量占位符不会被替换。
- **修复**：改为 `public static final`，使用时再格式化。

### 17. Git 换行符警告
- 大量 `LF will be replaced by CRLF` 警告，根目录缺少 `.gitattributes`。
- **修复**：根目录添加 `.gitattributes`：`* text=auto`。

### 18. HELP.md 无意义内容
- `backend/HELP.md` 是 Spring Initializr 自动生成的模板，无实际价值，可删除。

---

## 五、建议的 .gitignore 模板

```gitignore
# IDE
.idea/
.vscode/
*.iml

# 后端
/backend/target/
/backend/node_modules/
/backend/*.log
/backend/.reasonrix/

# 前端（用户端 + 管理后台）
/frontend/node_modules/
/frontend/dist/
/admin-frontend/node_modules/
/admin-frontend/dist/

# 本地配置（含密钥）
/backend/**/application-local.yaml
/frontend/.env
/admin-frontend/.env

# AI 助手工作目录
.workbuddy/
```

---

## 六、优先级行动清单

| 优先级 | 事项 | 工作量 |
|--------|------|--------|
| P0 | 吊销泄露的 API Key，敏感配置移出 Git | 小 |
| P0 | 替换 JWT Secret 为随机密钥 | 小 |
| P0 | 管理员密码改 BCrypt | 小 |
| P1 | 修复 admin-frontend request.js 字段名 bug | 极小 |
| P1 | 提交 admin-frontend 代码 + 完善 .gitignore | 小 |
| P1 | 修复 Maven Wrapper | 小 |
| P2 | 分页改用 Page 对象 | 中 |
| P2 | admin-frontend 添加 Vite 代理 + 404 路由 | 小 |
| P3 | 清理模板文件 / Constant / HELP.md | 极小 |
