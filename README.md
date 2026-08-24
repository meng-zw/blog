# 小M的个人博客

一个面向公开阅读的个人博客：展示个人经历与思考，发布文章、随笔、专题，并整理常用提效工具。
访客无需创建账号或登录；只有站点管理员可以进入后台维护内容。评论功能暂未开放。

## 技术栈

- 前端：Vue 3、TypeScript、Vite、Vue Router、Pinia、Element Plus、Vditor
- 后端：Spring Boot 3.5、Java 21、Spring Data JPA、Spring Security、Flyway
- 数据库：MySQL 8.0+
- 内容：Markdown 服务端渲染与 HTML 白名单清洗
- 认证：单管理员服务端 Session、CSRF 防护与登录限流

## 功能边界

- 公开页面：首页、文章与随笔、专题、工具、搜索、关于、站点地图
- 管理后台：文章、专题、分类与标签、工具、媒体、站点资料和管理员密码
- 不提供访客账号、公开写作入口及社区互动能力
- 媒体只通过后台上传；默认使用持久化 Local 目录，也可切换 Cloudflare R2

## 本地开发

### 环境要求

- JDK 21+
- Maven 3.9+
- Node.js 20+
- MySQL 8.0+

### 后端

开发配置默认连接本机 `blog` 数据库。请通过环境或本地配置提供数据库凭据，并为首次启动
设置管理员引导变量；不要将密钥提交到仓库。

```bash
cd blog-backend
export BLOG_ADMIN_USERNAME=<admin-name>
export BLOG_ADMIN_PASSWORD=<strong-password>
mvn spring-boot:run
```

默认 API 地址为 `http://localhost:8081/api`，健康检查位于 Actuator 内部端点。

### 前端

```bash
cd blog-frontend
npm ci
npm run dev
```

默认页面地址为 `http://localhost:5173`。开发代理配置见 `blog-frontend/vite.config.ts`。

## 验证

```bash
cd blog-backend
mvn test
mvn package -DskipTests

# CI / 发布门禁：Docker 不可用时必须失败，禁止把集成测试跳过当成通过
mvn -Dblog.requireDockerTests=true test

cd ../blog-frontend
npm run test:run
npm run typecheck
npm run build
npm run lint:legacy-routes
```

依赖 Docker 的 MySQL 集成测试应在具备 Docker 的 CI 或发布环境使用上述强制门禁运行；也可设置
`BLOG_REQUIRE_DOCKER_TESTS=true`。本地默认允许缺少 Docker 时跳过。本地迁移旧内容前，
必须先阅读 [旧内容迁移与备份说明](docs/migration/legacy-content-migration.md)，完成只读计数与备份门槛。

## 配置

主要环境变量：

- `BLOG_PUBLIC_BASE_URL`：公开站点根地址
- `BLOG_MEDIA_PROVIDER`：`local`（默认）或 `r2`
- `BLOG_MEDIA_LOCAL_DIRECTORY`：Local 媒体存储目录
- `BLOG_MEDIA_R2_*`：R2 仅由 API 读取的账户、桶、S3 endpoint 与公开域名变量
- `BLOG_ADMIN_USERNAME`：首次启动时创建的管理员用户名
- `BLOG_ADMIN_PASSWORD`：首次启动时创建的管理员密码
- `BLOG_ADMIN_DISPLAY_NAME`：管理员显示名，默认“小M”

生产部署必须使用独立密钥管理和最小权限数据库账号，并由反向代理终止 HTTPS。R2 桶、最小权限
API Token、CORS、密钥轮换、对象迁移和回退见 [媒体存储运行手册](docs/media-storage.md)。容器、代理及
CI 配置在发布验收任务中统一固化。
