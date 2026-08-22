# 小M个人博客改造设计

## 1. 背景与目标

现有项目以多用户内容社区为模型，包含注册、普通用户个人中心、用户投稿、点赞、收藏和评论等能力；前后端职责混杂，后端同时引入 JPA 与 MyBatis-Plus，核心业务集中在体积过大的 Controller 中，缺乏自动化测试，配置中还存在不适合生产环境的默认密钥和调试设置。

本次改造将产品收敛为单作者个人博客，以展示“小M”、分享文章、随笔、专题和提效工具为核心。公开访客无需注册或登录，只能阅读公开内容；唯一管理员通过后台写作和管理站点内容。项目保留 Vue 3、Spring Boot 和 MySQL 技术栈，但重新整理页面结构、数据模型、API、后端分层、认证机制、测试和部署方式，使其达到可发布上线的标准。

## 2. 品牌与视觉目标

- 博客主标题：小M的思与行。
- 副标题与个人简介：中庸之道。
- 昵称：小M。
- GitHub：https://github.com/meng-zw。
- 头像/个人标识来源：用户提供的黑底水墨徽标图片 `codex-clipboard-9cff6579-7310-4c7a-ae80-9a1772dd0c54.png`。
- 视觉参考来源：用户提供的博客首页参考图 `codex-clipboard-9da9feaf-d73e-4bfc-b5a5-ba1fa168fd66.png`。

视觉实现遵循参考图的设计语言和主要比例：暖米白纸张背景、深棕墨色文字、宋体/衬线字体、大幅横向主视觉、细边框、克制圆角、低饱和图片和杂志式内容网格。附件徽标保持黑底完整构图，在暖色页面中以深色徽章承载，不做破坏笔触或印章的强制裁切。

桌面端首页尽量还原参考图的空间关系和内容密度；移动端重排为单列阅读流，保留视觉层级而不是机械缩放桌面页面。公开站点使用自研轻量组件和设计令牌，不继承 Element Plus 的默认外观；后台可以继续使用 Element Plus 提升表单、表格和编辑器效率。

## 3. 首期产品范围

### 3.1 公开页面

- `/`：顶栏、沉浸式主视觉、本周精选、最新文章、推荐工具、主题入口、关于小M、GitHub 外链和页脚。
- `/articles`：文章列表，支持分类、标签和关键词筛选。
- `/articles/:slug`：文章封面、摘要、目录、Markdown 正文、上一篇和下一篇。
- `/topics`：专题列表及系列文章聚合。
- `/notes`：随笔列表。随笔与普通文章共用文章模型，以内容类型区分。
- `/tools`：提效工具列表，支持类别和关键词筛选。
- `/tools/:slug`：工具详情、适用场景、使用说明和外部链接。
- `/about`：个人标识、昵称、简介和 GitHub。
- `/search`：文章、随笔、专题和工具的统一搜索结果。

公开文章和工具详情不显示点赞、收藏或评论。固定页面可在顶栏中配置展示顺序；后台入口不出现在公开导航中。

### 3.2 管理后台

后台统一位于 `/admin`：

- `/admin/login`：唯一管理员登录。
- `/admin`：内容概览、草稿、待发布内容和最近更新。
- `/admin/articles`：文章与随笔的创建、编辑、预览、发布、定时发布和归档。
- `/admin/topics`：专题管理和文章编排。
- `/admin/taxonomy`：分类和标签管理。
- `/admin/tools`：提效工具管理、推荐状态和排序。
- `/admin/media`：图片上传、替代文本和引用状态。
- `/admin/settings`：站名、昵称、简介、头像和社交链接。
- `/admin/account`：修改管理员密码和退出其他会话。

### 3.3 明确不在首期范围内

- 访客注册、普通用户登录和个人中心。
- 访客投稿或多作者协作。
- 点赞、收藏和用户行为关系。
- 评论提交、评论展示和评论审核。
- 邮件订阅、退订和周刊发送。
- Redis 缓存、全文搜索服务和对象存储等独立基础设施。

首期前端不展示上述能力的入口，后端也不暴露相应公开写接口。数据库不保留旧社区模型中的普通用户、点赞、收藏和评论业务表。

## 4. 系统架构

### 4.1 总体架构

生产环境采用同源部署：Nginx 作为 HTTPS 入口，提供前端静态文件和缓存策略，并将 `/api/**` 反向代理到 Spring Boot。Spring Boot 提供公开查询 API、管理员 API、认证、媒体上传、站点地图和健康检查。MySQL 保存业务数据，上传文件保存在独立持久化目录。

首期不使用 Redis。管理员只有一个，服务端会话可直接由 Spring Security 管理；文章和工具规模也不需要额外缓存。若后续访问量、横向扩容或搜索规模证明有需要，再通过独立设计引入 Redis 或搜索引擎。

### 4.2 后端模块边界

后端仅使用 Spring Data JPA，移除 MyBatis-Plus。代码按业务能力组织：

- `identity`：唯一管理员、密码、登录、退出和会话。
- `article`：文章、随笔、草稿、定时发布和相邻内容导航。
- `topic`：专题和专题文章排序。
- `taxonomy`：分类、标签及其关联。
- `tool`：提效工具、分类、推荐状态和排序。
- `media`：图片元数据、上传验证和文件定位。
- `site`：公开站点资料和首页聚合。
- `search`：基于数据库的首期统一搜索。
- `shared`：审计字段、分页、Problem Details、追踪 ID 和公共配置。

每个模块遵守 Controller → Application Service → Repository 的依赖方向。Controller 只处理 HTTP 协议、输入校验和 DTO 转换；Application Service 承担事务、权限之外的业务规则和跨仓储编排；Repository 只负责持久化查询。实体不直接作为 API 响应返回。

### 4.3 前端模块边界

- `app/public`：公开站点布局、导航、页脚和主题。
- `app/admin`：后台布局、会话恢复和路由保护。
- `features/articles`、`features/topics`、`features/tools`、`features/search`、`features/site`、`features/media`：各业务能力的 API、类型、组件和页面。
- `shared/api`：类型化 HTTP 客户端和 Problem Details 解析。
- `shared/ui`：按钮、卡片、图片、分页、空态和错误态。
- `shared/lib`：日期、URL、Markdown 和图片工具。

公开页面使用自研组件；后台按需使用 Element Plus。路由组件懒加载。Pinia 只保存管理员会话和必要的跨页面 UI 状态，服务端资源仍由功能模块负责获取和刷新。

## 5. 数据模型

所有业务表使用明确的主键、唯一约束、外键、创建时间和更新时间。生产环境禁用 Hibernate 自动改表，由 Flyway 管理迁移。

### 5.1 `admin_account`

- `id`、`username`、`password_hash`、`display_name`。
- `enabled`、`password_changed_at`、`created_at`、`updated_at`。
- 系统规则保证仅有一个启用的管理员账号。

### 5.2 `site_profile`

- `id`，固定为单行站点配置。
- `site_title`、`subtitle`、`nickname`、`bio`。
- `avatar_media_id`、`github_url`。
- `updated_at`。

初始内容为“小M的思与行”“中庸之道”“小M”和用户提供的 GitHub 链接。

### 5.3 `article`

- `id`、唯一 `slug`、`title`、`summary`。
- `markdown_content`、清洗后的 `rendered_html`。
- `content_type`：`ARTICLE` 或 `NOTE`。
- `status`：`DRAFT`、`SCHEDULED`、`PUBLISHED`、`ARCHIVED`。
- `cover_media_id`、`category_id`、`topic_id`。
- `published_at`、`scheduled_at`、`created_at`、`updated_at`。
- `seo_title`、`seo_description`。

文章与标签通过关联表建立多对多关系。只有 `PUBLISHED` 且 `published_at` 不晚于当前时间的内容可由公开 API 返回。定时任务只负责把到期的 `SCHEDULED` 内容转换为 `PUBLISHED`，并保证重复执行安全。

### 5.4 `topic`、`category` 与 `tag`

- `topic`：`id`、唯一 `slug`、`name`、`description`、`cover_media_id`、`status`、排序字段。
- `category`：`id`、唯一 `slug`、`name`、`scope`、排序字段。
- `tag`：`id`、唯一 `slug`、`name`。
- 专题文章关联保存显式顺序，分类通过 `scope` 区分文章与工具用途。

### 5.5 `tool`

- `id`、唯一 `slug`、`name`、`summary`、`description_markdown`、清洗后的 `rendered_html`。
- `official_url`、`cover_media_id`、`category_id`。
- `status`：`DRAFT`、`PUBLISHED`、`ARCHIVED`。
- `featured`、排序字段、`published_at`、`created_at`、`updated_at`。

工具与标签通过关联表建立多对多关系。公开 API 只返回已发布工具。

### 5.6 `media_asset`

- `id`、`storage_key`、原始文件名、`content_type`、字节大小、宽高。
- `alt_text`、`created_at`。
- 文件本体位于独立持久化目录，数据库只保存受控相对键，不保存任意本地路径。

## 6. API 与数据流

### 6.1 公开 API

- `GET /api/public/home`：站点资料、精选文章、最新文章和推荐工具的聚合结果。
- `GET /api/public/articles`、`GET /api/public/articles/{slug}`。
- `GET /api/public/topics`、`GET /api/public/topics/{slug}`。
- `GET /api/public/tools`、`GET /api/public/tools/{slug}`。
- `GET /api/public/search`。
- `GET /api/public/site-profile`。
- `GET /api/sitemap.xml`。

列表接口统一使用有上限的分页参数，支持必要的分类、标签、内容类型和关键词筛选。公开 DTO 不包含草稿、内部文件路径或管理员信息。

### 6.2 管理员 API

- `POST /api/admin/session`、`DELETE /api/admin/session`、`GET /api/admin/session`。
- `/api/admin/articles/**`、`/api/admin/topics/**`、`/api/admin/taxonomy/**`。
- `/api/admin/tools/**`、`/api/admin/media/**`、`/api/admin/settings/**`。
- `PUT /api/admin/account/password`。

所有管理员写接口需要有效会话与 CSRF token。创建或更新内容时先校验 DTO，再由 Application Service 检查 slug 唯一性、状态转换、发布时间和关联对象合法性，最后在事务中保存。成功响应返回面向界面的 DTO，不泄漏实体内部结构。

### 6.3 首页与局部失败

首页聚合服务以有限查询返回首屏需要的数据，避免前端发出大量相互依赖的请求。公开首页整体请求失败时显示可重试错误态；非关键图片加载失败使用设计一致的占位，不用硬编码文章或工具伪装真实数据。后台各页面区分加载中、空数据、校验失败、网络失败和权限失效状态。

## 7. 认证与安全

### 7.1 管理员认证

- 使用 Spring Security 服务端会话，不再使用存放于 `localStorage` 的 JWT。
- 会话 Cookie 使用 `HttpOnly`、生产环境 `Secure` 和 `SameSite=Lax`。
- 登录成功后轮换 Session ID；退出时销毁服务端会话并清除 Cookie。
- 注册接口永久移除。管理员账号通过部署环境变量首次初始化，初始化完成后可在后台修改密码。
- 登录失败返回统一提示，不泄漏账号是否存在；按来源与账号实施有限窗口限流并记录安全事件。
- 后台路由守卫只负责体验，最终权限始终由服务端检查。

### 7.2 请求与内容安全

- 所有状态变更请求启用 CSRF 防护。
- 生产环境采用同源部署，不允许通配来源 CORS。
- DTO 使用 Jakarta Validation，明确长度、格式、枚举和分页上限。
- Markdown 保存原文；服务端渲染后执行 HTML 白名单清洗，再将安全 HTML 提供给前端。
- 上传仅允许配置的图片格式，校验扩展名、MIME、文件签名、大小和尺寸；使用随机存储键，拒绝路径穿越和可执行内容。
- 数据库密码、管理员初始密码和其他敏感配置仅从环境变量读取；仓库只提交 `.env.example`。
- 生产环境关闭 SQL 输出、Security debug 和敏感异常细节。

## 8. 异常、日志与可观测性

API 错误统一采用 RFC 9457 Problem Details，至少包含 `type`、`title`、`status`、`detail`、`instance` 和 `traceId`。分别处理参数校验失败、资源不存在、唯一约束/状态冲突、未认证、无权限、上传拒绝和未预期异常。

每个请求生成或透传追踪 ID。日志使用结构化字段记录时间、级别、traceId、请求方法、路径、耗时和结果，不记录密码、会话值、CSRF token 或完整敏感请求体。应用提供存活和就绪健康检查；数据库不可用时就绪检查失败，但不泄漏连接信息。

## 9. SEO、性能与可访问性

- 每个公开页面设置标题、描述、canonical 和 Open Graph 信息。
- 文章详情输出 Article JSON-LD；后端根据已发布内容生成 `sitemap.xml`。
- 提供 `robots.txt`，禁止索引 `/admin`。
- 首页主视觉和封面使用压缩后的 WebP/AVIF，并保留兼容格式；图片使用响应式尺寸、懒加载、明确宽高和替代文本。
- 首屏资源控制体积，路由和后台编辑器按需加载。
- 使用语义化标题层级、键盘可用交互、可见焦点和符合 WCAG AA 的文本对比度。
- 动效尊重 `prefers-reduced-motion`。

## 10. 测试策略

### 10.1 后端

- 单元测试覆盖 slug 生成、文章状态转换、定时发布幂等性、媒体校验和站点配置规则。
- Spring Security 测试覆盖未登录访问、管理员写操作、CSRF、退出和会话失效。
- 使用 Testcontainers + MySQL 的集成测试验证 Flyway、Repository 查询、公开内容过滤和关键 API。
- Controller 契约测试验证成功 DTO、分页、Validation 和 Problem Details。

### 10.2 前端

- 使用 Vitest 和 Vue Test Utils 覆盖首页区块、列表空态、错误态、登录表单、后台路由保护和编辑表单。
- TypeScript 类型检查作为构建前置条件。
- 使用 Playwright 覆盖管理员登录、创建草稿、发布文章、公开访问、退出登录和移动端公开导航。
- 对首页、文章详情、工具列表、后台文章编辑页进行桌面与移动端视觉检查。

### 10.3 完成门槛

交付前必须同时满足：后端测试通过、前端测试通过、类型检查通过、前后端生产构建成功、核心 Playwright 流程通过、Flyway 可在空数据库完整执行、Docker Compose 健康检查通过，并完成桌面端与移动端关键页面的人工视觉复核。

## 11. 部署设计

Docker Compose 包含：

- Nginx：HTTPS 入口、静态资源缓存、SPA 路由回退和 `/api` 反向代理。
- Spring Boot：使用非 root 用户运行，暴露内部应用端口和健康检查。
- MySQL：使用固定版本、utf8mb4、健康检查和持久化卷。
- 媒体目录：独立持久化卷，由后端写入、Nginx 或后端受控读取。

项目提供 `.env.example`、开发与生产配置、启动顺序、数据库与媒体备份/恢复说明、日志轮转建议和升级步骤。镜像使用固定版本标签，不使用 `latest`。CI 执行测试与构建，部署前先备份数据库和媒体目录，再运行 Flyway 并检查服务健康状态。

## 12. 数据迁移与清理

当前项目以重新整理数据模型为主，不要求维持旧表结构兼容。实施时先盘点现有文章、工具、分类、标签和上传文件；若存在需要保留的真实内容，则提供一次性迁移脚本，将可映射字段导入新表并生成稳定 slug，迁移结果通过数量和抽样内容校验。测试用户、点赞、收藏、评论和其他社区关系不迁移。

旧 API 和前端路由在新能力完成并通过验收后删除，不长期维护双轨实现。任何真实数据迁移和旧表删除都必须先完成可恢复备份。

## 13. 验收标准

- 未登录访客可以访问全部已发布文章、随笔、专题、工具、搜索和关于页面，页面中没有注册、登录、点赞、收藏、评论或订阅入口。
- 访客无法调用任何内容写接口，也不能通过猜测后台 URL 绕过服务端权限。
- 唯一管理员能够登录后台，管理站点资料、媒体、文章、专题、分类、标签和工具，并能完成草稿、预览、发布、定时发布与归档流程。
- 首页在桌面端呈现与参考图一致的暖纸杂志风格，在移动端形成清晰单列阅读流。
- 站点显示“小M的思与行”“中庸之道”、用户提供的徽标和 GitHub 链接。
- API 错误格式统一，无假数据回退，无实体或敏感字段泄漏。
- 生产配置不包含仓库内硬编码密钥，不使用 Hibernate 自动改表，不输出 SQL 或 Security debug 日志。
- 自动化测试、生产构建、数据库迁移、Docker Compose 健康检查和关键页面视觉复核均通过。
