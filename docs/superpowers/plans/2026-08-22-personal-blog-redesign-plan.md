# 小M个人博客改造实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将现有多用户博客改造成视觉贴合参考图、仅唯一管理员可写、访客匿名阅读且可通过 Docker Compose 上线的“小M的思与行”个人博客。

**Architecture:** 保留 Vue 3、Spring Boot 3.5、Java 21 和 MySQL，后端按业务能力拆分并统一使用 Spring Data JPA、Flyway、服务端 Session 与 Problem Details；前端区分自研公开站组件和 Element Plus 管理后台。实施采用先建立新基础与新 API、再切换前端、最后删除旧社区代码的绞杀式顺序，每个任务结束时系统都可构建或测试。

**Tech Stack:** Vue 3.5、TypeScript 5.9、Vite 7、Pinia、Element Plus（仅后台）、Vitest、Vue Test Utils、Playwright、Spring Boot 3.5.9、Java 21、Spring Data JPA、Spring Security、Flyway、MySQL 8.4、Testcontainers、Docker Compose、Nginx。

**Spec:** `docs/superpowers/specs/2026-08-22-personal-blog-redesign.md`

## Global Constraints

- 公开访客无需注册或登录，只能读取已发布内容。
- 系统仅允许一个启用的管理员账号；无注册接口、普通用户、个人中心、点赞、收藏、评论或邮件订阅。
- 博客名称固定初始化为“小M的思与行”，副标题和简介为“中庸之道”，昵称为“小M”，GitHub 为 `https://github.com/meng-zw`。
- 公开站视觉使用暖米白纸张背景、深棕墨色、宋体/衬线字体、细边框和杂志式网格；Element Plus 只用于后台。
- 后端只使用 Spring Data JPA；移除 MyBatis-Plus、JWT 与 Redis。
- 生产数据库结构只由 Flyway 管理，`ddl-auto` 必须为 `validate`。
- 管理员认证使用服务端 Session、HttpOnly Cookie 和 CSRF；生产环境同源部署。
- 所有 API 错误使用 RFC 9457 Problem Details 并带 `traceId`。
- 所有图片上传校验扩展名、MIME、文件签名、大小和尺寸；Markdown 输出经过服务端白名单清洗。
- 完成声明前必须通过后端测试、前端测试、类型检查、生产构建、Flyway 空库迁移、Playwright 核心流程和 Docker Compose 健康检查。

---

## File Structure

### Backend

- `blog-backend/src/main/java/com/blog/identity/`: 唯一管理员、初始化、登录会话和密码修改。
- `blog-backend/src/main/java/com/blog/site/`: 站点资料及首页聚合。
- `blog-backend/src/main/java/com/blog/media/`: 图片元数据、验证和存储。
- `blog-backend/src/main/java/com/blog/taxonomy/`: 分类与标签。
- `blog-backend/src/main/java/com/blog/topic/`: 专题及文章排序。
- `blog-backend/src/main/java/com/blog/article/`: 文章、随笔、状态流转和定时发布。
- `blog-backend/src/main/java/com/blog/tool/`: 提效工具。
- `blog-backend/src/main/java/com/blog/search/`: 统一搜索。
- `blog-backend/src/main/java/com/blog/shared/`: 审计、错误、分页、追踪与安全公共配置。
- `blog-backend/src/main/resources/db/migration/`: Flyway 版本化数据库迁移。
- `blog-backend/src/test/java/com/blog/`: 与生产模块同构的测试。

### Frontend

- `blog-frontend/src/app/public/`: 公开站布局与路由壳。
- `blog-frontend/src/app/admin/`: 后台布局与路由壳。
- `blog-frontend/src/features/`: 按 `site`、`articles`、`topics`、`tools`、`search`、`media`、`session` 分组。
- `blog-frontend/src/shared/api/`: 类型化客户端与 Problem Details。
- `blog-frontend/src/shared/ui/`: 公开站基础组件、空态和错误态。
- `blog-frontend/src/styles/`: 设计令牌、基础样式、公开站布局和后台覆盖。
- `blog-frontend/e2e/`: Playwright 核心流程。

---

### Task 1: 建立可迁移、可测试的后端基础

**Files:**
- Modify: `blog-backend/pom.xml`
- Modify: `blog-backend/src/main/resources/application.yml`
- Create: `blog-backend/src/main/resources/application-dev.yml`
- Create: `blog-backend/src/main/resources/application-prod.yml`
- Create: `blog-backend/src/main/resources/db/migration/V1__create_personal_blog_schema.sql`
- Create: `blog-backend/src/test/resources/application-test.yml`
- Create: `blog-backend/src/test/java/com/blog/support/MySqlIntegrationTest.java`
- Create: `blog-backend/src/test/java/com/blog/support/FlywayMigrationTest.java`
- Delete after migration test passes: `blog-backend/src/main/resources/data.sql`

**Interfaces:**
- Produces: MySQL schema tables `admin_account`, `site_profile`, `media_asset`, `category`, `tag`, `topic`, `article`, `article_tag`, `topic_article`, `tool`, `tool_tag`.
- Produces: abstract `MySqlIntegrationTest` exposing a shared MySQL 8.4 Testcontainer to Spring tests.

- [ ] **Step 1: Replace mixed persistence and JWT dependencies**

Use `spring-boot-starter-parent` version `3.5.9`. Remove `mybatis-plus-boot-starter`, all `jjwt-*`, and explicit Redis-related configuration. Add these dependencies: `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `flyway-core`, `flyway-mysql`, `commonmark:0.24.0`, `jsoup:1.21.2`, `spring-security-test` in test scope, and `org.testcontainers:mysql` plus `org.testcontainers:junit-jupiter` version `1.21.3` in test scope.

- [ ] **Step 2: Write the failing Flyway migration test**

```java
package com.blog.support;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class FlywayMigrationTest extends MySqlIntegrationTest {
    @Autowired DataSource dataSource;

    @Test
    void migratesAnEmptyDatabaseToVersionOne() {
        var result = Flyway.configure().dataSource(dataSource).load().migrate();
        assertThat(result.success).isTrue();
        assertThat(result.targetSchemaVersion.getVersion()).isEqualTo("1");
    }
}
```

- [ ] **Step 3: Run the migration test and confirm failure**

Run: `cd blog-backend && mvn -Dtest=FlywayMigrationTest test`

Expected: FAIL because `MySqlIntegrationTest` and `V1__create_personal_blog_schema.sql` do not exist.

- [ ] **Step 4: Add the shared MySQL test base and V1 schema**

`MySqlIntegrationTest` must use `@Testcontainers`, one static `MySQLContainer<?>` with image `mysql:8.4`, and `@DynamicPropertySource` to set datasource URL, username and password. V1 must create the exact tables named above with `utf8mb4`, foreign keys, unique indexes on every slug, and an index on `(status, published_at)` for article/tool queries. `admin_account` must include `enabled_guard TINYINT GENERATED ALWAYS AS (CASE WHEN enabled = 1 THEN 1 ELSE NULL END) STORED` plus a unique index on `enabled_guard`; this permits historical disabled rows but makes a second enabled administrator impossible at the database layer.

Configure all profiles with `spring.jpa.hibernate.ddl-auto=validate`; enable Flyway; production datasource values come from `SPRING_DATASOURCE_*`; development may use `localhost:3306/blog` but must not contain a real production password.

- [ ] **Step 5: Run migration and application context tests**

Run: `cd blog-backend && mvn -Dtest=FlywayMigrationTest test`

Expected: PASS and Flyway reports target version `1`.

- [ ] **Step 6: Remove legacy SQL bootstrap and commit**

Run: `git rm blog-backend/src/main/resources/data.sql`

```bash
git add blog-backend/pom.xml blog-backend/src/main/resources blog-backend/src/test
git commit -m "build: establish flyway mysql foundation"
```

---

### Task 2: 统一审计字段、追踪 ID 和 Problem Details

**Files:**
- Create: `blog-backend/src/main/java/com/blog/shared/persistence/AuditedEntity.java`
- Create: `blog-backend/src/main/java/com/blog/shared/error/ResourceNotFoundException.java`
- Create: `blog-backend/src/main/java/com/blog/shared/error/ConflictException.java`
- Create: `blog-backend/src/main/java/com/blog/shared/error/GlobalExceptionHandler.java`
- Create: `blog-backend/src/main/java/com/blog/shared/web/TraceIdFilter.java`
- Create: `blog-backend/src/main/java/com/blog/shared/web/PageResponse.java`
- Create: `blog-backend/src/test/java/com/blog/shared/error/GlobalExceptionHandlerTest.java`
- Delete after replacement passes: `blog-backend/src/main/java/com/blog/exception/GlobalExceptionHandler.java`

**Interfaces:**
- Produces: `record PageResponse<T>(List<T> items, int page, int size, long total, int totalPages)`.
- Produces: every error response as `application/problem+json` with `type`, `title`, `status`, `detail`, `instance`, `traceId`, and optional `errors`.

- [ ] **Step 1: Write failing MVC tests for validation and not-found errors**

Create a test-only controller with `GET /test/not-found` throwing `new ResourceNotFoundException("article", "missing")` and `POST /test/validate` accepting a record whose `name` is `@NotBlank`. Assert status `404`, content type `application/problem+json`, detail `article not found: missing`, and nonblank `traceId`; assert invalid input returns `400` with `errors.name`.

- [ ] **Step 2: Run the focused test**

Run: `cd blog-backend && mvn -Dtest=GlobalExceptionHandlerTest test`

Expected: FAIL because shared error classes and the trace filter do not exist.

- [ ] **Step 3: Implement the shared web contract**

Use Spring `ProblemDetail.forStatusAndDetail`. `TraceIdFilter` must accept a valid incoming `X-Trace-Id` containing 8–64 alphanumeric, underscore, or hyphen characters; otherwise generate a UUID without dashes. Add it to response header `X-Trace-Id` and MDC key `traceId`, then clear MDC in `finally`.

`AuditedEntity` must be a `@MappedSuperclass` with `Instant createdAt` and `Instant updatedAt`, set by JPA auditing. `PageResponse.from(Page<T>)` must preserve page metadata.

- [ ] **Step 4: Run tests and delete the legacy handler**

Run: `cd blog-backend && mvn -Dtest=GlobalExceptionHandlerTest test`

Expected: PASS.

Run: `git rm blog-backend/src/main/java/com/blog/exception/GlobalExceptionHandler.java`

- [ ] **Step 5: Commit**

```bash
git add blog-backend/src/main/java/com/blog/shared blog-backend/src/test/java/com/blog/shared
git commit -m "feat: standardize api errors and tracing"
```

---

### Task 3: 实现唯一管理员与安全 Session

**Files:**
- Create: `blog-backend/src/main/java/com/blog/identity/AdminAccount.java`
- Create: `blog-backend/src/main/java/com/blog/identity/AdminAccountRepository.java`
- Create: `blog-backend/src/main/java/com/blog/identity/AdminBootstrapProperties.java`
- Create: `blog-backend/src/main/java/com/blog/identity/AdminBootstrap.java`
- Create: `blog-backend/src/main/java/com/blog/identity/AdminUserDetailsService.java`
- Create: `blog-backend/src/main/java/com/blog/identity/LoginAttemptService.java`
- Create: `blog-backend/src/main/java/com/blog/identity/AdminSessionController.java`
- Create: `blog-backend/src/main/java/com/blog/identity/AdminAccountController.java`
- Create: `blog-backend/src/main/java/com/blog/identity/dto/LoginRequest.java`
- Create: `blog-backend/src/main/java/com/blog/identity/dto/SessionResponse.java`
- Create: `blog-backend/src/main/java/com/blog/identity/dto/ChangePasswordRequest.java`
- Replace: `blog-backend/src/main/java/com/blog/config/SecurityConfig.java`
- Create: `blog-backend/src/test/java/com/blog/identity/AdminSessionControllerTest.java`
- Create: `blog-backend/src/test/java/com/blog/identity/AdminBootstrapTest.java`
- Create: `blog-backend/src/test/java/com/blog/identity/LoginAttemptServiceTest.java`

**Interfaces:**
- Consumes: `admin_account` from Task 1 and Problem Details from Task 2.
- Produces: `POST/GET/DELETE /api/admin/session`, `PUT /api/admin/account/password`.
- Produces: `SessionResponse(boolean authenticated, String username, String displayName)`.

- [ ] **Step 1: Write failing security tests**

Test these exact behaviors with MockMvc: `GET /api/public/ping` is permitted; `GET /api/admin/session` returns `200` with `authenticated=false`; an unauthenticated `POST /api/admin/articles` with CSRF returns `401`; login with the configured account and `.with(csrf())` creates a session and rotates its ID; login without CSRF returns `403`; logout invalidates the session.

- [ ] **Step 2: Run the tests to verify failure**

Run: `cd blog-backend && mvn -Dtest=AdminSessionControllerTest,AdminBootstrapTest test`

Expected: FAIL because the new identity model and endpoints do not exist.

- [ ] **Step 3: Implement single-admin bootstrap**

Bind `blog.admin.bootstrap.username`, `password`, and `display-name`. In production, fail startup when no administrator exists and either username or password is blank. When the table is empty, create one BCrypt-hashed account. When an enabled account already exists, do not overwrite its password on restart.

`AdminAccountRepository` exposes `Optional<AdminAccount> findByUsernameAndEnabledTrue(String username)` and `long countByEnabledTrue()`.

- [ ] **Step 4: Implement stateful security**

Configure `SessionCreationPolicy.IF_REQUIRED`, CSRF cookie repository with a JavaScript-readable CSRF token cookie named `XSRF-TOKEN`, session fixation protection, maximum one active session for the administrator, and headers including CSP, frame denial, referrer policy and content type options. Permit only `GET /api/public/**`, `GET /api/sitemap.xml`, `GET /api/media/**`, actuator health, and the session endpoint behavior defined above; require role `ADMIN` for `/api/admin/**`.

Configure the session Cookie as `HttpOnly`, `SameSite=Lax`, and `Secure` in production. Do not enable wildcard CORS.

- [ ] **Step 5: Add login throttling and password change**

Create `LoginAttemptService` with a maximum of 10,000 keys, keyed by normalized username plus client IP: maximum 5 failures in 15 minutes, successful login clears failures, oldest expired entries are removed before the bound is enforced, and the oldest key is evicted when capacity is reached. `PUT /api/admin/account/password` requires current password, a new password of 12–72 characters, and confirmation; changing the password invalidates other sessions through Spring Security's `SessionRegistry` while retaining the current session.

- [ ] **Step 6: Run tests and commit**

Run: `cd blog-backend && mvn -Dtest=AdminSessionControllerTest,AdminBootstrapTest test`

Expected: PASS.

```bash
git add blog-backend/src/main/java/com/blog/identity blog-backend/src/main/java/com/blog/config/SecurityConfig.java blog-backend/src/test/java/com/blog/identity blog-backend/src/main/resources/application*.yml
git commit -m "feat: add secure single-admin sessions"
```

---

### Task 4: 实现媒体资产与站点资料

**Files:**
- Create: `blog-backend/src/main/java/com/blog/media/MediaAsset.java`
- Create: `blog-backend/src/main/java/com/blog/media/MediaAssetRepository.java`
- Create: `blog-backend/src/main/java/com/blog/media/MediaStorageService.java`
- Create: `blog-backend/src/main/java/com/blog/media/MediaController.java`
- Create: `blog-backend/src/main/java/com/blog/media/MediaProperties.java`
- Create: `blog-backend/src/main/java/com/blog/site/SiteProfile.java`
- Create: `blog-backend/src/main/java/com/blog/site/SiteProfileRepository.java`
- Create: `blog-backend/src/main/java/com/blog/site/SiteProfileService.java`
- Create: `blog-backend/src/main/java/com/blog/site/PublicSiteController.java`
- Create: `blog-backend/src/main/java/com/blog/site/AdminSiteController.java`
- Create: `blog-backend/src/main/java/com/blog/site/dto/SiteProfileResponse.java`
- Create: `blog-backend/src/main/java/com/blog/site/dto/UpdateSiteProfileRequest.java`
- Create: `blog-backend/src/test/java/com/blog/media/MediaStorageServiceTest.java`
- Create: `blog-backend/src/test/java/com/blog/site/SiteProfileControllerTest.java`
- Create during implementation: `blog-frontend/public/images/xiao-m-mark.png` from the user-provided avatar attachment.

**Interfaces:**
- Produces: `GET /api/public/site-profile`, `GET/PUT /api/admin/settings`, `POST /api/admin/media`, `GET /api/media/{storageKey}`.
- Produces: `SiteProfileResponse(siteTitle, subtitle, nickname, bio, avatarUrl, githubUrl)`.

- [ ] **Step 1: Write failing file validation tests**

Test that a valid PNG under 5 MiB is stored under a UUID-based key and returns dimensions; a `.png` containing HTML bytes is rejected; SVG, files over 5 MiB, images over 6000×6000, and filenames containing path separators are rejected with status `400`.

- [ ] **Step 2: Write failing site-profile API tests**

Assert public defaults are exactly `小M的思与行`, `中庸之道`, `小M`, `中庸之道`, and `https://github.com/meng-zw`; unauthenticated update returns `401`; authenticated update rejects non-HTTPS social links.

- [ ] **Step 3: Run focused tests**

Run: `cd blog-backend && mvn -Dtest=MediaStorageServiceTest,SiteProfileControllerTest test`

Expected: FAIL because media and site modules do not exist.

- [ ] **Step 4: Implement media validation and site-profile services**

Use `ImageIO` to decode PNG, JPEG and GIF; compare the decoded format with the allowlisted content type, enforce byte and dimension limits, and write via a temporary file followed by atomic move into `${BLOG_MEDIA_DIR}`. WebP and AVIF remain supported as build-time static frontend assets but are not accepted by the first-release upload endpoint. Never concatenate an untrusted path segment.

Seed the single site profile through Flyway `V2__seed_site_profile.sql` with a nullable `avatar_media_id`. Store the avatar attachment as `blog-frontend/public/images/xiao-m-mark.png`; `SiteProfileService` returns `/images/xiao-m-mark.png` when `avatar_media_id` is null and returns `/api/media/{storageKey}` after the administrator uploads a replacement.

- [ ] **Step 5: Run tests and commit**

Run: `cd blog-backend && mvn -Dtest=MediaStorageServiceTest,SiteProfileControllerTest test`

Expected: PASS.

```bash
git add blog-backend/src/main/java/com/blog/media blog-backend/src/main/java/com/blog/site blog-backend/src/main/resources/db/migration/V2__seed_site_profile.sql blog-backend/src/test/java/com/blog/media blog-backend/src/test/java/com/blog/site blog-frontend/public/images/xiao-m-mark.png
git commit -m "feat: add media and site profile management"
```

---

### Task 5: 实现分类、标签与专题

**Files:**
- Create: `blog-backend/src/main/java/com/blog/taxonomy/Category.java`
- Create: `blog-backend/src/main/java/com/blog/taxonomy/Tag.java`
- Create: `blog-backend/src/main/java/com/blog/taxonomy/CategoryScope.java`
- Create: `blog-backend/src/main/java/com/blog/taxonomy/CategoryRepository.java`
- Create: `blog-backend/src/main/java/com/blog/taxonomy/TagRepository.java`
- Create: `blog-backend/src/main/java/com/blog/taxonomy/TaxonomyService.java`
- Create: `blog-backend/src/main/java/com/blog/taxonomy/TaxonomyController.java`
- Create: `blog-backend/src/main/java/com/blog/taxonomy/dto/CategoryRequest.java`
- Create: `blog-backend/src/main/java/com/blog/taxonomy/dto/CategoryResponse.java`
- Create: `blog-backend/src/main/java/com/blog/taxonomy/dto/TagRequest.java`
- Create: `blog-backend/src/main/java/com/blog/taxonomy/dto/TagResponse.java`
- Create: `blog-backend/src/main/java/com/blog/topic/Topic.java`
- Create: `blog-backend/src/main/java/com/blog/topic/TopicArticle.java`
- Create: `blog-backend/src/main/java/com/blog/topic/TopicRepository.java`
- Create: `blog-backend/src/main/java/com/blog/topic/TopicArticleRepository.java`
- Create: `blog-backend/src/main/java/com/blog/topic/TopicService.java`
- Create: `blog-backend/src/main/java/com/blog/topic/AdminTopicController.java`
- Create: `blog-backend/src/main/java/com/blog/topic/PublicTopicController.java`
- Create: `blog-backend/src/main/java/com/blog/topic/dto/TopicWriteRequest.java`
- Create: `blog-backend/src/main/java/com/blog/topic/dto/TopicResponse.java`
- Create: `blog-backend/src/test/java/com/blog/taxonomy/TaxonomyServiceTest.java`
- Create: `blog-backend/src/test/java/com/blog/topic/TopicServiceTest.java`

**Interfaces:**
- Produces: category scopes `ARTICLE` and `TOOL`; topic statuses `DRAFT` and `PUBLISHED`.
- Produces: admin CRUD endpoints under `/api/admin/taxonomy` and `/api/admin/topics`.
- Produces: `TaxonomyService.requireCategory(long id, CategoryScope scope)` and `Set<Tag> TaxonomyService.requireTags(Set<Long> ids)`.

- [ ] **Step 1: Write failing domain tests**

Test case-insensitive uniqueness for category/tag names, slug uniqueness, rejection when an article uses a tool category, rejection when deleting a referenced category, and stable explicit ordering of topic articles.

- [ ] **Step 2: Run focused tests**

Run: `cd blog-backend && mvn -Dtest=TaxonomyServiceTest,TopicServiceTest test`

Expected: FAIL because taxonomy and topic services do not exist.

- [ ] **Step 3: Implement entities, repositories, DTOs and services**

Normalize names with trim plus Unicode NFKC before uniqueness checks. Generate lowercase URL-safe slugs and append `-2`, `-3` only when a collision exists. Deleting a referenced taxonomy returns `409 Conflict`; do not cascade-delete content. Topic reorder accepts the complete ordered article ID list and stores contiguous zero-based positions in one transaction.

- [ ] **Step 4: Run tests and commit**

Run: `cd blog-backend && mvn -Dtest=TaxonomyServiceTest,TopicServiceTest test`

Expected: PASS.

```bash
git add blog-backend/src/main/java/com/blog/taxonomy blog-backend/src/main/java/com/blog/topic blog-backend/src/test/java/com/blog/taxonomy blog-backend/src/test/java/com/blog/topic
git commit -m "feat: add taxonomy and topic domains"
```

---

### Task 6: 实现文章、随笔和安全 Markdown 发布流

**Files:**
- Create: `blog-backend/src/main/java/com/blog/article/Article.java`
- Create: `blog-backend/src/main/java/com/blog/article/ArticleStatus.java`
- Create: `blog-backend/src/main/java/com/blog/article/ContentType.java`
- Create: `blog-backend/src/main/java/com/blog/article/ArticleRepository.java`
- Create: `blog-backend/src/main/java/com/blog/article/MarkdownRenderer.java`
- Create: `blog-backend/src/main/java/com/blog/article/ArticleService.java`
- Create: `blog-backend/src/main/java/com/blog/article/ArticlePublishScheduler.java`
- Create: `blog-backend/src/main/java/com/blog/article/PublicArticleController.java`
- Create: `blog-backend/src/main/java/com/blog/article/AdminArticleController.java`
- Create: `blog-backend/src/main/java/com/blog/article/dto/ArticleWriteRequest.java`
- Create: `blog-backend/src/main/java/com/blog/article/dto/ArticleSummaryResponse.java`
- Create: `blog-backend/src/main/java/com/blog/article/dto/ArticleDetailResponse.java`
- Create: `blog-backend/src/test/java/com/blog/article/MarkdownRendererTest.java`
- Create: `blog-backend/src/test/java/com/blog/article/ArticleServiceTest.java`
- Create: `blog-backend/src/test/java/com/blog/article/ArticleSecurityIntegrationTest.java`

**Interfaces:**
- Consumes: taxonomy/topic/media services from Tasks 4–5.
- Produces: `ArticleService.createDraft`, `update`, `publishNow`, `schedule`, `archive`, `findPublishedBySlug`, and `publishDue(Instant now)`.
- Produces: public `/api/public/articles` and `/api/public/articles/{slug}`; admin `/api/admin/articles/**`.

- [ ] **Step 1: Write failing Markdown security tests**

Assert headings, fenced Java code and links render; `<script>`, inline event handlers, `javascript:` URLs and iframes are removed; external links receive `rel="noopener noreferrer"`; generated heading IDs are stable and unique.

- [ ] **Step 2: Write failing article state tests**

Cover legal transitions `DRAFT→PUBLISHED`, `DRAFT→SCHEDULED`, `SCHEDULED→PUBLISHED`, and published content to `ARCHIVED`; reject scheduling in the past; make `publishDue(now)` idempotent; ensure public queries exclude drafts, archived rows, and future publish times; ensure previous/next navigation only uses visible content of the same `ContentType`.

- [ ] **Step 3: Run focused tests**

Run: `cd blog-backend && mvn -Dtest=MarkdownRendererTest,ArticleServiceTest,ArticleSecurityIntegrationTest test`

Expected: FAIL because the article module does not exist.

- [ ] **Step 4: Implement article domain and endpoints**

`ArticleWriteRequest` validates title 1–200, summary 1–500, Markdown 1–200000, slug up to 160, SEO title up to 70 and SEO description up to 160. Use CommonMark for rendering and Jsoup safelist customization for cleaning. Re-render only when Markdown changes. Increment no view counter in the request path.

Use database pagination directly. The scheduler runs once per minute and performs a conditional update or locked batch so two invocations cannot publish twice. Public detail returns cover URL, category, tags, topic, safe rendered HTML and previous/next summaries.

- [ ] **Step 5: Run article tests and commit**

Run: `cd blog-backend && mvn -Dtest=MarkdownRendererTest,ArticleServiceTest,ArticleSecurityIntegrationTest test`

Expected: PASS.

```bash
git add blog-backend/src/main/java/com/blog/article blog-backend/src/test/java/com/blog/article
git commit -m "feat: add secure article publishing workflow"
```

---

### Task 7: 实现提效工具内容域

**Files:**
- Create: `blog-backend/src/main/java/com/blog/tool/Tool.java`
- Create: `blog-backend/src/main/java/com/blog/tool/ToolStatus.java`
- Create: `blog-backend/src/main/java/com/blog/tool/ToolRepository.java`
- Create: `blog-backend/src/main/java/com/blog/tool/ToolService.java`
- Create: `blog-backend/src/main/java/com/blog/tool/PublicToolController.java`
- Create: `blog-backend/src/main/java/com/blog/tool/AdminToolController.java`
- Create: `blog-backend/src/main/java/com/blog/tool/dto/ToolWriteRequest.java`
- Create: `blog-backend/src/main/java/com/blog/tool/dto/ToolSummaryResponse.java`
- Create: `blog-backend/src/main/java/com/blog/tool/dto/ToolDetailResponse.java`
- Create: `blog-backend/src/test/java/com/blog/tool/ToolServiceTest.java`
- Create: `blog-backend/src/test/java/com/blog/tool/ToolControllerIntegrationTest.java`

**Interfaces:**
- Consumes: `MarkdownRenderer`, taxonomy and media services.
- Produces: public `/api/public/tools` and `/api/public/tools/{slug}`; admin `/api/admin/tools/**`.

- [ ] **Step 1: Write failing tool tests**

Test draft exclusion, published filtering by tool category/tag/keyword, featured ordering before explicit sort order, slug conflict handling, rejection of non-HTTP(S) official URLs, archived content returning `404`, and sanitized detail Markdown.

- [ ] **Step 2: Run focused tests**

Run: `cd blog-backend && mvn -Dtest=ToolServiceTest,ToolControllerIntegrationTest test`

Expected: FAIL because the tool module does not exist.

- [ ] **Step 3: Implement tool domain and endpoints**

`ToolWriteRequest` validates name 1–100, summary 1–500, description 1–100000, official URL as absolute HTTPS by default, and explicit category/tag IDs. Admin reorder receives ordered IDs and persists contiguous positions transactionally. Public lists sort by featured descending, explicit position ascending, then published time descending.

- [ ] **Step 4: Run tests and commit**

Run: `cd blog-backend && mvn -Dtest=ToolServiceTest,ToolControllerIntegrationTest test`

Expected: PASS.

```bash
git add blog-backend/src/main/java/com/blog/tool blog-backend/src/test/java/com/blog/tool
git commit -m "feat: add productivity tool publishing"
```

---

### Task 8: 实现首页聚合、统一搜索与站点地图

**Files:**
- Create: `blog-backend/src/main/java/com/blog/site/HomeQueryService.java`
- Create: `blog-backend/src/main/java/com/blog/site/dto/HomeResponse.java`
- Create: `blog-backend/src/main/java/com/blog/search/SearchService.java`
- Create: `blog-backend/src/main/java/com/blog/search/PublicSearchController.java`
- Create: `blog-backend/src/main/java/com/blog/search/dto/SearchResultResponse.java`
- Create: `blog-backend/src/main/java/com/blog/site/SitemapController.java`
- Create: `blog-backend/src/test/java/com/blog/site/HomeQueryIntegrationTest.java`
- Create: `blog-backend/src/test/java/com/blog/search/SearchIntegrationTest.java`
- Create: `blog-backend/src/test/java/com/blog/site/SitemapControllerTest.java`

**Interfaces:**
- Produces: `GET /api/public/home`, `GET /api/public/search?q=&page=&size=`, `GET /api/sitemap.xml`.
- Produces: `HomeResponse(site, featuredArticle, latestArticles, featuredTools, topics)`.

- [ ] **Step 1: Write failing public-query tests**

Seed published, draft, scheduled-future and archived records. Assert home/search/sitemap contain only visible rows; search rejects blank or over-100-character queries; pagination size is clamped to 1–50; XML includes canonical article, topic and tool URLs and excludes `/admin`.

- [ ] **Step 2: Run focused tests**

Run: `cd blog-backend && mvn -Dtest=HomeQueryIntegrationTest,SearchIntegrationTest,SitemapControllerTest test`

Expected: FAIL because aggregate/search/sitemap services do not exist.

- [ ] **Step 3: Implement bounded database queries**

Build home from one site query plus bounded repository queries: one featured article, four latest articles, four featured tools, and four published topics. Implement case-insensitive title/summary/name search with JPA specifications and a discriminated result type `ARTICLE`, `NOTE`, `TOPIC`, or `TOOL`. Generate XML with Spring `SitemapController`, set `application/xml`, and escape all URL content.

- [ ] **Step 4: Run all backend tests and commit**

Run: `cd blog-backend && mvn test`

Expected: PASS with no MyBatis or JWT classes loaded.

```bash
git add blog-backend/src/main/java/com/blog/site blog-backend/src/main/java/com/blog/search blog-backend/src/test/java/com/blog/site blog-backend/src/test/java/com/blog/search
git commit -m "feat: add public home search and sitemap"
```

---

### Task 9: 建立前端测试基础、类型化 API 与新路由壳

**Files:**
- Modify: `blog-frontend/package.json`
- Modify: `blog-frontend/vite.config.ts`
- Modify: `blog-frontend/tsconfig.json`
- Replace: `blog-frontend/src/utils/axios.ts` with `blog-frontend/src/shared/api/http.ts`
- Create: `blog-frontend/src/shared/api/problem.ts`
- Create: `blog-frontend/src/shared/api/contracts.ts`
- Replace: `blog-frontend/src/router/index.ts`
- Replace: `blog-frontend/src/App.vue`
- Modify: `blog-frontend/src/main.ts`
- Create: `blog-frontend/src/app/public/PublicLayout.vue`
- Create: `blog-frontend/src/app/admin/AdminLayout.vue`
- Create: `blog-frontend/src/shared/ui/AppError.vue`
- Create: `blog-frontend/src/shared/ui/AppEmpty.vue`
- Create: `blog-frontend/src/shared/api/http.test.ts`

**Interfaces:**
- Produces: `http.get<T>`, `http.post<T>`, `http.put<T>`, `http.delete<T>` with `credentials: 'include'` and CSRF header support.
- Produces: lazy routes for all public and `/admin` pages from the spec.

- [ ] **Step 1: Add front-end test dependencies and scripts**

Add dev dependencies `vitest`, `@vue/test-utils`, `jsdom`, `@testing-library/vue`, `@playwright/test`, and `vite-plugin-vue-devtools` only if it is disabled in production. Add scripts `test`, `test:run`, `test:e2e`, and `typecheck`; keep `build` as `npm run typecheck && vite build`.

- [ ] **Step 2: Write failing HTTP client tests**

Mock `fetch` and assert cookies are included, JSON is decoded, `XSRF-TOKEN` is copied to `X-XSRF-TOKEN` for POST/PUT/DELETE, a `401` does not force `window.location` navigation, and `application/problem+json` becomes a typed `ApiProblem` with `traceId`.

- [ ] **Step 3: Run the test to confirm failure**

Run: `cd blog-frontend && npm run test:run -- src/shared/api/http.test.ts`

Expected: FAIL because the shared API client does not exist.

- [ ] **Step 4: Implement the API client and route shells**

Define exact DTOs matching Tasks 3–8; do not use `any`. Public routes use `PublicLayout`; admin routes use `AdminLayout`; `/login`, `/register`, `/profile`, `/write`, `/share-tool` and old edit routes are absent. Import Element Plus styles only from the admin entry/layout path rather than globally.

- [ ] **Step 5: Run tests, typecheck and commit**

Run: `cd blog-frontend && npm run test:run && npm run typecheck`

Expected: PASS.

```bash
git add blog-frontend/package.json blog-frontend/package-lock.json blog-frontend/vite.config.ts blog-frontend/tsconfig.json blog-frontend/src
git commit -m "refactor: establish typed frontend application shell"
```

---

### Task 10: 实现参考图风格设计系统与公开首页

**Files:**
- Replace: `blog-frontend/src/design-tokens.css` with `blog-frontend/src/styles/tokens.css`
- Replace: `blog-frontend/src/style.css` with `blog-frontend/src/styles/base.css`
- Create: `blog-frontend/src/styles/public-layout.css`
- Create: `blog-frontend/src/features/site/api.ts`
- Create: `blog-frontend/src/features/site/pages/HomePage.vue`
- Create: `blog-frontend/src/features/site/components/SiteHeader.vue`
- Create: `blog-frontend/src/features/site/components/HeroSection.vue`
- Create: `blog-frontend/src/features/site/components/FeaturedGrid.vue`
- Create: `blog-frontend/src/features/site/components/TopicStrip.vue`
- Create: `blog-frontend/src/features/site/components/AboutPanel.vue`
- Create: `blog-frontend/src/features/site/components/SiteFooter.vue`
- Create: `blog-frontend/src/features/site/pages/HomePage.test.ts`
- Create during implementation: `blog-frontend/public/images/hero-workspace.webp`
- Create during implementation: `blog-frontend/public/images/hero-workspace.avif`

**Interfaces:**
- Consumes: `GET /api/public/home` and `SiteProfileResponse`.
- Produces: responsive public homepage matching the approved reference structure.

- [ ] **Step 1: Write failing homepage component tests**

Mock the home API and assert the rendered page contains “小M的思与行”, “中庸之道”, one featured article, latest articles, recommended tools, topic links, “关于小M”, and the GitHub URL. Add separate tests for loading, empty optional sections and recoverable API error; assert there is no registration, login, comment, favorite, like or subscription control.

- [ ] **Step 2: Run the homepage test**

Run: `cd blog-frontend && npm run test:run -- src/features/site/pages/HomePage.test.ts`

Expected: FAIL because the new homepage does not exist.

- [ ] **Step 3: Generate and optimize the hero image**

Use the `imagegen` skill with this prompt: “A wide editorial photograph for a Chinese personal blog hero, warm morning sunlight across a pale oak writing desk, open cream notebook with fountain pen, ceramic coffee mug, two understated productivity books, small branch in a glass vase, edge of a dark laptop, quiet contemplative mood, warm beige and deep brown palette, natural realistic photography, generous negative space on the left for Chinese headline, no people, no visible logos, no readable text, 16:5 panoramic composition.” Export a 2400-pixel-wide master, then produce AVIF quality 55 and WebP quality 72. Do not place text inside the bitmap.

- [ ] **Step 4: Implement design tokens and homepage**

Use background `#f6f0e7`, surface `#fbf8f2`, ink `#2d251e`, secondary ink `#6f6257`, border `#ded4c6`, accent `#8a5b3d`. Use a Chinese serif stack headed by `Songti SC` for editorial content and a system sans stack for controls. Desktop content width is 1280px; header height 72px; hero aspect ratio approximately 3.2:1; cards use 1px borders and at most 6px radius. At widths below 768px, collapse navigation into an accessible menu and all content grids to one column.

Implement the reference ordering: header → hero → weekly feature/latest grid → topic strip → about/GitHub panel → footer. Preserve semantic heading order and keyboard focus styles.

- [ ] **Step 5: Run tests and production build**

Run: `cd blog-frontend && npm run test:run -- src/features/site/pages/HomePage.test.ts && npm run build`

Expected: PASS and Vite emits optimized production assets.

- [ ] **Step 6: Commit**

```bash
git add blog-frontend/src/styles blog-frontend/src/features/site blog-frontend/src/app/public blog-frontend/public/images blog-frontend/src/main.ts
git commit -m "feat: rebuild homepage in editorial paper style"
```

---

### Task 11: 实现公开文章、专题、工具、搜索与关于页面

**Files:**
- Create: `blog-frontend/src/features/articles/api.ts`
- Create: `blog-frontend/src/features/articles/pages/ArticleListPage.vue`
- Create: `blog-frontend/src/features/articles/pages/ArticleDetailPage.vue`
- Create: `blog-frontend/src/features/articles/components/ArticleCard.vue`
- Create: `blog-frontend/src/features/articles/components/ArticleToc.vue`
- Create: `blog-frontend/src/features/topics/pages/TopicListPage.vue`
- Create: `blog-frontend/src/features/topics/pages/TopicDetailPage.vue`
- Create: `blog-frontend/src/features/topics/api.ts`
- Create: `blog-frontend/src/features/tools/api.ts`
- Create: `blog-frontend/src/features/tools/pages/ToolListPage.vue`
- Create: `blog-frontend/src/features/tools/pages/ToolDetailPage.vue`
- Create: `blog-frontend/src/features/search/pages/SearchPage.vue`
- Create: `blog-frontend/src/features/site/pages/AboutPage.vue`
- Create: `blog-frontend/src/shared/lib/seo.ts`
- Create: `blog-frontend/public/robots.txt`
- Create: `blog-frontend/src/features/articles/pages/ArticleDetailPage.test.ts`
- Create: `blog-frontend/src/features/search/pages/SearchPage.test.ts`

**Interfaces:**
- Consumes: public APIs from Tasks 6–8.
- Produces: complete public route set with typed loading, empty, error and not-found states.

- [ ] **Step 1: Write failing public-page tests**

For article detail, assert title/summary/safe HTML/TOC/previous-next links, canonical URL, Open Graph values and Article JSON-LD. For search, assert a blank query shows guidance without sending a request, filters render discriminated result types, and an empty result is not replaced with mock data. Assert notes route sends `contentType=NOTE`.

- [ ] **Step 2: Run focused tests**

Run: `cd blog-frontend && npm run test:run -- src/features/articles/pages/ArticleDetailPage.test.ts src/features/search/pages/SearchPage.test.ts`

Expected: FAIL because public content pages do not exist.

- [ ] **Step 3: Implement all public pages and SEO helper**

`seo.ts` must set document title, description, canonical, Open Graph tags and an optional JSON-LD script with a stable DOM ID so route changes replace rather than duplicate metadata. Render server-sanitized article/tool HTML only through one reviewed component. External links receive `target="_blank"` plus `rel="noopener noreferrer"`. Every list uses API pagination and preserves filters in the URL query.

- [ ] **Step 4: Run public tests, accessibility smoke checks and build**

Run: `cd blog-frontend && npm run test:run && npm run typecheck && npm run build`

Expected: PASS with no TypeScript `any` in the new feature directories.

- [ ] **Step 5: Commit**

```bash
git add blog-frontend/src/features blog-frontend/src/shared/lib/seo.ts blog-frontend/public/robots.txt
git commit -m "feat: add complete public reading experience"
```

---

### Task 12: 实现后台会话、导航与站点资料管理

**Files:**
- Create: `blog-frontend/src/features/session/store.ts`
- Create: `blog-frontend/src/features/session/api.ts`
- Create: `blog-frontend/src/features/session/pages/AdminLoginPage.vue`
- Create: `blog-frontend/src/features/session/pages/AdminLoginPage.test.ts`
- Create: `blog-frontend/src/app/admin/AdminSidebar.vue`
- Create: `blog-frontend/src/app/admin/AdminTopbar.vue`
- Create: `blog-frontend/src/features/site/pages/AdminDashboardPage.vue`
- Create: `blog-frontend/src/features/site/pages/AdminSettingsPage.vue`
- Create: `blog-frontend/src/features/media/pages/AdminMediaPage.vue`
- Create: `blog-frontend/src/router/adminGuard.ts`
- Create: `blog-frontend/src/router/adminGuard.test.ts`

**Interfaces:**
- Consumes: session, settings and media APIs from Tasks 3–4.
- Produces: `useSessionStore()` with `restore()`, `login(credentials)`, `logout()` and `isAuthenticated`.

- [ ] **Step 1: Write failing login and guard tests**

Assert direct navigation to `/admin/articles` calls session restore and redirects unauthenticated users to `/admin/login?redirect=/admin/articles`; authenticated users proceed; login validation requires username and password; server `401` displays the returned generic message; successful login uses the safe same-origin `redirect` value; logout returns to the public homepage.

- [ ] **Step 2: Run focused tests**

Run: `cd blog-frontend && npm run test:run -- src/features/session/pages/AdminLoginPage.test.ts src/router/adminGuard.test.ts`

Expected: FAIL because session UI and guard do not exist.

- [ ] **Step 3: Implement session state and admin shell**

The store must never persist credentials or session identifiers. It may keep `SessionResponse` in memory and restore it via `GET /api/admin/session` on page reload. Validate redirects by resolving them against `window.location.origin` and only accept paths beginning with `/admin/`.

Build sidebar links for overview, articles, topics, taxonomy, tools, media, settings and account. Settings must edit the approved site fields and preview the full black-background avatar without destructive cropping.

- [ ] **Step 4: Run tests and commit**

Run: `cd blog-frontend && npm run test:run && npm run typecheck`

Expected: PASS.

```bash
git add blog-frontend/src/features/session blog-frontend/src/app/admin blog-frontend/src/features/site/pages/AdminDashboardPage.vue blog-frontend/src/features/site/pages/AdminSettingsPage.vue blog-frontend/src/features/media blog-frontend/src/router
git commit -m "feat: add secure admin shell and settings"
```

---

### Task 13: 实现后台文章、专题、分类标签与工具管理

**Files:**
- Create: `blog-frontend/src/features/articles/pages/AdminArticleListPage.vue`
- Create: `blog-frontend/src/features/articles/pages/AdminArticleEditorPage.vue`
- Create: `blog-frontend/src/features/articles/components/MarkdownEditor.vue`
- Create: `blog-frontend/src/features/articles/components/PublishDialog.vue`
- Create: `blog-frontend/src/features/articles/pages/AdminArticleEditorPage.test.ts`
- Create: `blog-frontend/src/features/topics/pages/AdminTopicPage.vue`
- Create: `blog-frontend/src/features/topics/pages/AdminTaxonomyPage.vue`
- Create: `blog-frontend/src/features/tools/pages/AdminToolListPage.vue`
- Create: `blog-frontend/src/features/tools/pages/AdminToolEditorPage.vue`
- Create: `blog-frontend/src/features/session/pages/AdminAccountPage.vue`

**Interfaces:**
- Consumes: admin APIs from Tasks 3 and 5–7.
- Produces: complete administrator content workflow without any visitor/community management UI.

- [ ] **Step 1: Write failing article-editor tests**

Test create draft, edit existing draft, unsaved-change navigation warning, Markdown preview, field validation, publish now, schedule with a future Asia/Shanghai time, archive confirmation, upload/choose cover image, and display of `409` slug conflicts. Assert no author selector, comment moderation, like, favorite or subscriber controls exist.

- [ ] **Step 2: Run the focused test**

Run: `cd blog-frontend && npm run test:run -- src/features/articles/pages/AdminArticleEditorPage.test.ts`

Expected: FAIL because the content editor does not exist.

- [ ] **Step 3: Implement administrator content screens**

Use Vditor only inside `MarkdownEditor.vue` and lazy-load it on editor routes. Convert local datetime input to ISO-8601 with explicit `+08:00`. Lists use server pagination and URL filters. Destructive actions require a confirmation dialog and only update UI after a successful response. Topic article ordering and tool ordering use keyboard-accessible move-up/move-down controls in addition to drag interactions.

- [ ] **Step 4: Run all frontend tests and build**

Run: `cd blog-frontend && npm run test:run && npm run typecheck && npm run build`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add blog-frontend/src/features/articles blog-frontend/src/features/topics blog-frontend/src/features/tools blog-frontend/src/features/session/pages/AdminAccountPage.vue
git commit -m "feat: add administrator content management"
```

---

### Task 14: 删除旧社区实现并验证无死代码

**Files:**
- Delete: `blog-backend/src/main/java/com/blog/controller/`
- Delete: `blog-backend/src/main/java/com/blog/entity/`
- Delete: `blog-backend/src/main/java/com/blog/repository/`
- Delete: `blog-backend/src/main/java/com/blog/service/impl/UserDetailsServiceImpl.java`
- Delete: `blog-backend/src/main/java/com/blog/service/impl/ArticlePublishScheduler.java`
- Delete: `blog-backend/src/main/java/com/blog/config/JwtAuthenticationFilter.java`
- Delete: `blog-backend/src/main/java/com/blog/utils/JwtUtils.java`
- Delete: legacy Vue views under `blog-frontend/src/views/`
- Delete: obsolete `blog-frontend/src/components/Logo.vue`, `SearchBar.vue`, `Icon.vue` when no new route imports them.
- Modify: `README.md`
- Create: `docs/migration/legacy-content-migration.md`
- Create if real content is found: `blog-backend/src/main/resources/db/migration/V5__migrate_legacy_content.sql`
- Create: `blog-backend/src/test/java/com/blog/architecture/LegacyDependencyGuardTest.java`

**Interfaces:**
- Consumes: all replacement modules from Tasks 1–13.
- Produces: one coherent API and UI with no legacy community route or dependency.

- [ ] **Step 1: Inventory real legacy content before deletion**

Run read-only SQL against the selected source database to count articles, tools, categories, tags and upload references. Record counts and backup commands in `docs/migration/legacy-content-migration.md`. If counts beyond seed/test content are nonzero, create V5 mapping only article/tool/taxonomy/media fields; generate stable slugs; exclude test users, likes, favorites and comments. Require a database and media backup before running V5 outside tests.

- [ ] **Step 2: Add architecture guard tests**

Add a plain JUnit `LegacyDependencyGuardTest` that walks `src/main/java`, reads every `.java` file, and fails with the matching file paths if source contains `com.blog.controller`, `com.blog.entity`, `com.blog.repository`, `io.jsonwebtoken`, or `com.baomidou.mybatisplus`. Add `lint:legacy-routes` to `package.json` using `rg` against `src/router` and invert the exit status so matches for `/register`, `/profile`, `/write`, `/share-tool`, `favorite`, or public comment routes fail the script while no matches pass.

- [ ] **Step 3: Run guards and confirm they initially fail**

Run: `cd blog-backend && mvn -Dtest=ArchitectureTest test`

Run: `cd blog-frontend && npm run lint:legacy-routes`

Expected: FAIL while legacy source files and routes still exist.

- [ ] **Step 4: Remove exact legacy files and references**

Use `git rm` on the listed legacy directories/files only after confirming new routes and APIs pass. Remove MyBatis, JWT, legacy `/auth`, comments, likes, favorites and registration mentions from configuration, README and imports. Preserve user-owned files outside the listed paths.

- [ ] **Step 5: Run complete local verification and commit**

Run: `cd blog-backend && mvn test`

Run: `cd blog-frontend && npm run test:run && npm run typecheck && npm run build && npm run lint:legacy-routes`

Expected: all commands PASS and `rg -n "MyBatis|JWT|注册|收藏|点赞|评论管理" README.md blog-backend/src/main blog-frontend/src` returns no active feature claims.

```bash
git add -A blog-backend/src blog-frontend/src README.md docs/migration
git commit -m "refactor: remove legacy community implementation"
```

---

### Task 15: 完成容器部署、CI、E2E 与上线验收

**Files:**
- Modify: `blog-backend/Dockerfile`
- Modify: `blog-frontend/Dockerfile`
- Modify: `blog-frontend/nginx/default.conf.template`
- Create: `docker-compose.yml`
- Create: `.env.example`
- Create: `.env.test.example`
- Create: `.github/workflows/ci.yml`
- Create: `blog-frontend/playwright.config.ts`
- Create: `blog-frontend/e2e/admin-publish.spec.ts`
- Create: `blog-frontend/e2e/public-mobile.spec.ts`
- Create: `docs/deployment.md`
- Create: `scripts/backup.sh`
- Create: `scripts/restore.sh`

**Interfaces:**
- Produces: services `web`, `api`, and `db`; volumes `mysql-data` and `media-data`.
- Produces: health endpoints `/actuator/health/liveness` and `/actuator/health/readiness`.

- [ ] **Step 1: Write failing Playwright journeys**

`admin-publish.spec.ts` logs in with CI credentials, creates a draft titled `发布流程验收`, publishes it, verifies it appears at its slug on the public site, logs out, and verifies the admin editor redirects to login. `public-mobile.spec.ts` uses a 390×844 viewport and verifies navigation, hero, article card, tool card, about/GitHub link, no horizontal overflow, and absence of registration/comment/subscription UI.

- [ ] **Step 2: Run E2E and confirm infrastructure failure**

Run: `cd blog-frontend && npm run test:e2e`

Expected: FAIL because Compose services and Playwright web server configuration do not exist.

- [ ] **Step 3: Harden images and Compose**

Backend image must run as a non-root UID, execute `mvn test` before packaging, expose only port 8081 inside the Compose network, mount `/app/media`, and include an internal readiness healthcheck. Frontend Nginx must set immutable caching for hashed assets, no-cache for `index.html`, proxy `/api/`, serve media through the API path, forward `X-Forwarded-*`, and add baseline security headers. Actuator remains reachable only inside the Compose network and is not proxied by Nginx.

Compose uses `mysql:8.4`, waits for DB health before API, waits for API readiness before web, reads secrets from `.env`, and never publishes the DB port in the production configuration. `.env.example` names `MYSQL_DATABASE`, `MYSQL_USER`, `MYSQL_PASSWORD`, `MYSQL_ROOT_PASSWORD`, `BLOG_ADMIN_USERNAME`, `BLOG_ADMIN_PASSWORD`, `BLOG_ADMIN_DISPLAY_NAME`, and `PUBLIC_BASE_URL` without real credentials. `.env.test.example` contains deterministic local-only credentials (`blog_test` / `blog_test_password_2026` and administrator `admin` / `Admin_test_password_2026`) and is never used as a production environment file.

- [ ] **Step 4: Add deterministic backup and restore scripts**

`backup.sh` must create a timestamped directory containing `mysqldump --single-transaction --routines --triggers` output plus a compressed media archive, write SHA-256 checksums, and refuse an empty destination. `restore.sh` must require an explicit backup directory, verify checksums, print target database/container names, and require interactive confirmation before replacing data.

- [ ] **Step 5: Add CI**

CI runs on pull requests and main pushes with Java 21 and Node 22: backend `mvn test`, frontend `npm ci`, `npm run test:run`, `npm run typecheck`, `npm run build`, Docker image builds, then Playwright against Compose. Cache Maven and npm downloads, but never cache `.env` or media.

- [ ] **Step 6: Run full release verification**

Run: `cd blog-backend && mvn clean verify`

Run: `cd blog-frontend && npm ci && npm run test:run && npm run typecheck && npm run build`

Run: `cp .env.test.example .env.test`

Run: `docker compose --env-file .env.test up --build -d`

Run: `docker compose ps`

Expected: `web`, `api`, and `db` are healthy.

Run: `cd blog-frontend && npm run test:e2e`

Expected: all Playwright projects PASS.

Render and visually inspect desktop 1440×900 and mobile 390×844 screenshots for `/`, `/articles`, one article detail, `/tools`, `/about`, `/admin/login`, and `/admin/articles/new`. Compare the desktop homepage against the supplied reference for header height, hero proportions, content grid, warm palette, typography, borders and spacing; fix visible deviations before completion.

- [ ] **Step 7: Commit deployment and release work**

```bash
git add blog-backend/Dockerfile blog-frontend/Dockerfile blog-frontend/nginx docker-compose.yml .env.example .env.test.example .github blog-frontend/playwright.config.ts blog-frontend/e2e docs/deployment.md scripts
git commit -m "build: add production deployment and release checks"
```

---

## Plan Self-Review Checklist

- Every product requirement in the approved spec maps to at least one task.
- The public site contains no registration, login promotion, user center, likes, favorites, comments or subscriptions.
- The administrator session, CSRF, upload validation, Markdown sanitization and environment secrets are tested before legacy JWT removal.
- New API/frontend routes exist before old routes are deleted.
- Data backup and inventory precede any migration or legacy table removal.
- Backend and frontend types, endpoint paths, status enums and DTO names are consistent across tasks.
- Every task has a focused failure signal, passing verification command and isolated commit.
