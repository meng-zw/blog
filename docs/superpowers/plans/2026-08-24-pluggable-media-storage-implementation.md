# Pluggable Media Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an in-process media module that supports Local and Cloudflare R2 storage, stable media-ID URLs, Vditor image uploads, and public article attachments without coupling article code to a storage vendor.

**Architecture:** `MediaApplicationService` owns media state and delegates object operations to a capability-aware `ObjectStorage`. R2 returns presigned direct-upload plans while Local uses an authenticated proxy endpoint; content stores `/api/media/assets/{id}` and normalized media references, so a provider migration does not rewrite Markdown.

**Tech Stack:** Java 21, Spring Boot 3.5.9, Spring Data JPA, Flyway/MySQL 8, AWS SDK for Java v2 S3 client/presigner, Vue 3, TypeScript, Vditor, Vitest, Playwright.

**Spec:** `docs/superpowers/specs/2026-08-24-pluggable-media-storage-design.md`

## Global Constraints

- Implement Local and R2 providers only; keep GitHub/Gitee/OSS as future adapters.
- Persist stable `/api/media/assets/{mediaId}` references, never provider URLs, in Markdown.
- R2 credentials remain server-side and scoped to the configured bucket.
- Images allow PNG, JPEG, and GIF up to 5 MiB and 6000 pixels per dimension.
- Public attachments allow PDF, ZIP, TXT, DOCX, XLSX, and PPTX; default limits are 20 MiB, with ZIP up to 50 MiB.
- First release uploads permanent objects directly; it does not copy temporary objects into a permanent bucket.
- READY but unreferenced media is visible as unused and is not automatically deleted.
- Existing local media records and `/api/media/{storageKey}` URLs continue to work.
- Every behavior change starts with a failing focused test and ends with the focused test plus the relevant regression suite passing.

---

## File Structure

Backend files are grouped inside `com.blog.media`: domain types remain at the package root to match the existing codebase, storage contracts live in `com.blog.media.storage`, and R2-specific code lives in `com.blog.media.storage.r2`. `MediaApplicationService` owns state transitions; controllers only translate HTTP. `MediaContentValidator` owns type, signature, size, and image-dimension checks. Article reference parsing is isolated in `ArticleMediaReferenceService`.

Frontend media transport stays in `src/features/media/api.ts`; the reusable upload state machine is added as `uploader.ts`. Vditor and article attachments consume that module without knowing the active provider.

---

### Task 1: Evolve the media schema and domain model

**Files:**
- Create: `blog-backend/src/main/resources/db/migration/V9__add_pluggable_media_storage.sql`
- Create: `blog-backend/src/main/java/com/blog/media/MediaStatus.java`
- Create: `blog-backend/src/main/java/com/blog/media/MediaPurpose.java`
- Create: `blog-backend/src/main/java/com/blog/media/StorageProvider.java`
- Modify: `blog-backend/src/main/java/com/blog/media/MediaAsset.java`
- Modify: `blog-backend/src/main/java/com/blog/media/MediaAssetRepository.java`
- Create: `blog-backend/src/test/java/com/blog/media/MediaAssetMappingTest.java`
- Modify: `blog-backend/src/test/java/com/blog/support/FlywayMigrationTest.java`

**Interfaces:**
- Produces: enums `MediaStatus`, `MediaPurpose`, `StorageProvider`; entity accessors for provider, bucket, status, purpose, etag, confirmedAt, updatedAt; repository lookup `findByIdAndUploadedById(Long, Long)`.

- [ ] **Step 1: Write the failing entity mapping test**

Create a reflection-based test asserting the three enum fields, lifecycle timestamps, ETag and `(provider,bucket,storage_key)` table uniqueness are represented by the entity. Extend the Docker Flyway test to assert V9 migrates an existing V8 database and sets legacy rows to `LOCAL`, `READY`, and `INLINE_IMAGE`.

- [ ] **Step 2: Run the focused tests and verify failure**

Run: `cd blog-backend && mvn -Dtest=MediaAssetMappingTest,FlywayMigrationTest test`

Expected: `MediaAssetMappingTest` fails because the fields and enums do not exist; the Flyway test is skipped without Docker or fails at V9 assertions with Docker.

- [ ] **Step 3: Add the migration and domain fields**

V9 must add nullable columns first, backfill existing rows, make provider/status/purpose non-null, replace `uk_media_asset_storage_key` with `uk_media_asset_location(provider,bucket,storage_key)`, and add indexes on `(status,created_at)` and `(uploaded_by_id,status)`. Use enum strings, not ordinals. New records are initialized explicitly by the application service rather than relying on entity defaults.

- [ ] **Step 4: Run focused and architecture tests**

Run: `cd blog-backend && mvn -Dtest=MediaAssetMappingTest,FlywayMigrationTest,LegacyDependencyGuardTest test`

Expected: mapping and architecture tests pass; Flyway passes when Docker is available and otherwise reports its established skip.

- [ ] **Step 5: Commit**

```bash
git add blog-backend/src/main/resources/db/migration/V9__add_pluggable_media_storage.sql blog-backend/src/main/java/com/blog/media blog-backend/src/test/java/com/blog/media/MediaAssetMappingTest.java blog-backend/src/test/java/com/blog/support/FlywayMigrationTest.java
git commit -m "feat: evolve media asset domain"
```

### Task 2: Define the storage contract and content validator

**Files:**
- Create: `blog-backend/src/main/java/com/blog/media/storage/ObjectStorage.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/StorageCapabilities.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/ObjectUploadRequest.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/UploadTicket.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/StoredObject.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/UploadMode.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/ObjectStorageRegistry.java`
- Create: `blog-backend/src/main/java/com/blog/media/MediaContentValidator.java`
- Modify: `blog-backend/src/main/java/com/blog/media/MediaProperties.java`
- Create: `blog-backend/src/test/java/com/blog/media/storage/ObjectStorageRegistryTest.java`
- Create: `blog-backend/src/test/java/com/blog/media/MediaContentValidatorTest.java`

**Interfaces:**
- Produces: `ObjectStorage.provider()`, `capabilities()`, `createDirectUpload()`, `upload()`, `inspect()`, `openStream()`, `resolvePublicUrl()`, and `delete()`; `MediaContentValidator.validateDeclaration(...)` and `validateStoredContent(...)`.
- Consumes: Task 1 enums.

- [ ] **Step 1: Write failing contract tests**

Test registry selection by `StorageProvider`, duplicate provider rejection, purpose-specific MIME/size acceptance, path-like filename rejection, PNG/JPEG/GIF signature validation, image decode and 6000-pixel dimension rejection, and attachment signature validation for PDF/ZIP-based Office formats.

- [ ] **Step 2: Run and verify failure**

Run: `cd blog-backend && mvn -Dtest=ObjectStorageRegistryTest,MediaContentValidatorTest test`

Expected: compilation fails because storage contracts and validator are absent.

- [ ] **Step 3: Implement immutable contract records and validator**

Use records for request/result values. `UploadTicket` contains mode, method, URI, required headers and expiry. `StoredObject` contains key, content type, byte size and ETag. Keep all limits in `MediaProperties`; validation errors use precise `IllegalArgumentException` messages consumed by the existing exception handler.

- [ ] **Step 4: Run focused tests**

Run: `cd blog-backend && mvn -Dtest=ObjectStorageRegistryTest,MediaContentValidatorTest test`

Expected: all focused tests pass.

- [ ] **Step 5: Commit**

```bash
git add blog-backend/src/main/java/com/blog/media/storage blog-backend/src/main/java/com/blog/media/MediaContentValidator.java blog-backend/src/main/java/com/blog/media/MediaProperties.java blog-backend/src/test/java/com/blog/media/storage blog-backend/src/test/java/com/blog/media/MediaContentValidatorTest.java
git commit -m "feat: define media storage contract"
```

### Task 3: Replace local file coupling with LocalObjectStorage

**Files:**
- Create: `blog-backend/src/main/java/com/blog/media/storage/LocalObjectStorage.java`
- Create: `blog-backend/src/test/java/com/blog/media/storage/LocalObjectStorageTest.java`
- Modify: `blog-backend/src/main/java/com/blog/media/MediaStorageService.java`
- Modify: `blog-backend/src/test/java/com/blog/media/MediaStorageServiceTest.java`
- Modify: `blog-backend/src/main/java/com/blog/media/MediaController.java`
- Modify: `blog-backend/src/test/java/com/blog/media/MediaControllerTest.java`

**Interfaces:**
- Produces: working `PROXY` provider and legacy storage-key read route.
- Consumes: Task 2 storage contract and validator.

- [ ] **Step 1: Write failing local adapter tests**

Cover normalized UUID-based keys with purpose prefixes, atomic proxy upload, inspect/open/delete, traversal rejection, absent-object behavior and public URL resolution. Update legacy service/controller tests to require delegation rather than direct `Files` access.

- [ ] **Step 2: Run and verify failure**

Run: `cd blog-backend && mvn -Dtest=LocalObjectStorageTest,MediaStorageServiceTest,MediaControllerTest test`

Expected: `LocalObjectStorageTest` fails to compile and delegation assertions fail.

- [ ] **Step 3: Implement the local adapter and compatibility facade**

Move filesystem operations from `MediaStorageService` into `LocalObjectStorage`. Keep `MediaStorageService.store(MultipartFile)`, `load(String)` and `findByStorageKey(String)` temporarily as a deprecated compatibility facade so cover uploads and the old public route remain operational until Task 4 replaces callers.

- [ ] **Step 4: Run focused tests**

Run: `cd blog-backend && mvn -Dtest=LocalObjectStorageTest,MediaStorageServiceTest,MediaControllerTest test`

Expected: all tests pass and legacy URLs still return immutable cached files.

- [ ] **Step 5: Commit**

```bash
git add blog-backend/src/main/java/com/blog/media blog-backend/src/test/java/com/blog/media
git commit -m "refactor: isolate local media storage"
```

### Task 4: Add the media upload state machine and HTTP API

**Files:**
- Create: `blog-backend/src/main/java/com/blog/media/MediaApplicationService.java`
- Create: `blog-backend/src/main/java/com/blog/media/AdminMediaController.java`
- Create: `blog-backend/src/main/java/com/blog/media/PublicMediaController.java`
- Create: `blog-backend/src/main/java/com/blog/media/dto/MediaUploadRequest.java`
- Create: `blog-backend/src/main/java/com/blog/media/dto/MediaUploadPlanResponse.java`
- Create: `blog-backend/src/main/java/com/blog/media/dto/MediaResponse.java`
- Create: `blog-backend/src/main/java/com/blog/media/MediaUploadCleanupJob.java`
- Modify: `blog-backend/src/main/java/com/blog/media/MediaController.java`
- Create: `blog-backend/src/test/java/com/blog/media/MediaApplicationServiceTest.java`
- Create: `blog-backend/src/test/java/com/blog/media/AdminMediaControllerTest.java`
- Create: `blog-backend/src/test/java/com/blog/media/PublicMediaControllerTest.java`

**Interfaces:**
- Produces: `POST /admin/media/uploads`, `PUT /admin/media/uploads/{id}/content`, `POST /admin/media/{id}/complete`, `GET /media/assets/{id}`, `GET /media/assets/{id}/download`, and idempotent unused-media deletion.
- Consumes: Tasks 1-3.

- [ ] **Step 1: Write failing service tests**

Cover PENDING creation, generated purpose-prefixed key, DIRECT and PROXY plans, owner enforcement, proxy upload, HEAD/content validation, READY idempotency, FAILED cleanup, 24-hour ABANDONED cleanup, redirect resolution, and deletion refusal when the reference checker reports use.

- [ ] **Step 2: Write failing controller tests**

Assert administrator and CSRF requirements, request validation, snake_case wire JSON, proxy multipart/binary handling, stable 302 location, public READY-only reads, attachment disposition behavior and RFC 9457 errors.

- [ ] **Step 3: Run and verify failure**

Run: `cd blog-backend && mvn -Dtest=MediaApplicationServiceTest,AdminMediaControllerTest,PublicMediaControllerTest test`

Expected: compilation fails because service, DTOs and controllers are absent.

- [ ] **Step 4: Implement the state machine and endpoints**

Resolve the current administrator through the existing security service instead of accepting an owner ID from clients. Use `Clock` injection for expiry tests. Return relative stable URL `/api/media/assets/{id}` in `MediaResponse`. Keep old `MediaController` only for `/media/{storageKey}` and remove its admin upload endpoint after frontend callers migrate.

- [ ] **Step 5: Run focused and security tests**

Run: `cd blog-backend && mvn -Dtest=MediaApplicationServiceTest,AdminMediaControllerTest,PublicMediaControllerTest,AdminSessionControllerTest,ArticleSecurityIntegrationTest test`

Expected: all tests pass.

- [ ] **Step 6: Commit**

```bash
git add blog-backend/src/main/java/com/blog/media blog-backend/src/test/java/com/blog/media
git commit -m "feat: add media upload lifecycle API"
```

### Task 5: Persist article inline-media and public attachments

**Files:**
- Create: `blog-backend/src/main/resources/db/migration/V10__add_article_media_references.sql`
- Create: `blog-backend/src/main/java/com/blog/media/ArticleMedia.java`
- Create: `blog-backend/src/main/java/com/blog/media/ArticleMediaId.java`
- Create: `blog-backend/src/main/java/com/blog/media/ArticleMediaRole.java`
- Create: `blog-backend/src/main/java/com/blog/media/ArticleMediaRepository.java`
- Create: `blog-backend/src/main/java/com/blog/media/ArticleMediaReferenceService.java`
- Modify: `blog-backend/src/main/java/com/blog/article/dto/ArticleWriteRequest.java`
- Modify: `blog-backend/src/main/java/com/blog/article/dto/AdminArticleResponse.java`
- Modify: `blog-backend/src/main/java/com/blog/article/dto/ArticleDetailResponse.java`
- Modify: `blog-backend/src/main/java/com/blog/article/ArticleService.java`
- Create: `blog-backend/src/test/java/com/blog/media/ArticleMediaReferenceServiceTest.java`
- Modify: `blog-backend/src/test/java/com/blog/article/ArticleServiceTest.java`

**Interfaces:**
- Produces: Markdown ID extraction, synchronized INLINE/ATTACHMENT rows, `attachmentMediaIds` write input and public attachment metadata output.
- Consumes: stable URL and READY media from Task 4.

- [ ] **Step 1: Write failing parser/reference tests**

Test extraction only from `/api/media/assets/{positiveId}`, duplicate collapse, ignored code-fence examples, READY enforcement, replacement/removal on article update, ordered attachments, display-name fallback and media-in-use queries.

- [ ] **Step 2: Extend article service tests and verify failure**

Run: `cd blog-backend && mvn -Dtest=ArticleMediaReferenceServiceTest,ArticleServiceTest test`

Expected: compilation or assertions fail because requests, responses and synchronization do not exist.

- [ ] **Step 3: Add V10 and implement reference synchronization**

Create `article_media` with `(article_id,media_id,role)` primary key, role/display/sort columns, foreign keys with article cascade, and no media cascade. Parse Markdown using a CommonMark node visitor rather than a regex so code blocks are ignored. Synchronize references inside the same article transaction after the article has an ID.

- [ ] **Step 4: Return public attachment DTOs**

Each attachment response contains mediaId, displayName, contentType, byteSize and stable download URL. Article HTML continues to render the stable image URL, which the browser follows through the media redirect.

- [ ] **Step 5: Run focused and MySQL tests**

Run: `cd blog-backend && mvn -Dtest=ArticleMediaReferenceServiceTest,ArticleServiceTest,FlywayMigrationTest test`

Expected: unit tests pass; Flyway follows the existing Docker pass/skip policy.

- [ ] **Step 6: Commit**

```bash
git add blog-backend/src/main/resources/db/migration/V10__add_article_media_references.sql blog-backend/src/main/java/com/blog/media blog-backend/src/main/java/com/blog/article blog-backend/src/test/java/com/blog/media/ArticleMediaReferenceServiceTest.java blog-backend/src/test/java/com/blog/article/ArticleServiceTest.java
git commit -m "feat: track article media references"
```

### Task 6: Build the frontend upload client and Vditor integration

**Files:**
- Modify: `blog-frontend/src/shared/api/contracts.ts`
- Modify: `blog-frontend/src/features/media/api.ts`
- Modify: `blog-frontend/src/features/media/api.test.ts`
- Create: `blog-frontend/src/features/media/uploader.ts`
- Create: `blog-frontend/src/features/media/uploader.test.ts`
- Modify: `blog-frontend/src/features/articles/components/MarkdownEditor.vue`
- Create: `blog-frontend/src/features/articles/components/MarkdownEditor.test.ts`

**Interfaces:**
- Produces: `uploadMedia(file, purpose, onProgress): Promise<MediaAssetResponse>` supporting DIRECT and PROXY; Vditor handler inserts stable URL.
- Consumes: Task 4 wire API.

- [ ] **Step 1: Write failing transport tests**

Mock the request API and XMLHttpRequest/fetch boundary. Assert request-upload, DIRECT PUT with required headers, PROXY upload with CSRF credentials, complete call, progress propagation, no complete after PUT failure, and returned stable URL.

- [ ] **Step 2: Write failing editor tests**

Mock Vditor construction, invoke `upload.handler` with PNG, assert validation, progress state, `![filename](/api/media/assets/123)` insertion, Chinese error reporting, and no Markdown mutation on failure.

- [ ] **Step 3: Run and verify failure**

Run: `cd blog-frontend && npm run test:run -- src/features/media/uploader.test.ts src/features/media/api.test.ts src/features/articles/components/MarkdownEditor.test.ts`

Expected: tests fail because upload plans and handler are not implemented.

- [ ] **Step 4: Implement upload transport and Vditor handler**

Keep provider details out of the editor. Escape Markdown alt text, accept only declared image types, use the backend result URL, and preserve the textarea fallback. Do not configure Vditor's raw `upload.url` because it bypasses the shared CSRF/error contract.

- [ ] **Step 5: Run focused tests and typecheck**

Run: `cd blog-frontend && npm run test:run -- src/features/media/uploader.test.ts src/features/media/api.test.ts src/features/articles/components/MarkdownEditor.test.ts && npm run typecheck`

Expected: focused tests and typecheck pass.

- [ ] **Step 6: Commit**

```bash
git add blog-frontend/src/shared/api/contracts.ts blog-frontend/src/features/media blog-frontend/src/features/articles/components/MarkdownEditor.vue blog-frontend/src/features/articles/components/MarkdownEditor.test.ts
git commit -m "feat: upload Markdown images through media API"
```

### Task 7: Add article attachment administration and public downloads

**Files:**
- Modify: `blog-frontend/src/features/articles/admin-api.ts`
- Modify: `blog-frontend/src/features/articles/api.ts`
- Modify: `blog-frontend/src/features/articles/pages/AdminArticleEditorPage.vue`
- Modify: `blog-frontend/src/features/articles/pages/AdminArticleEditorPage.test.ts`
- Modify: `blog-frontend/src/features/articles/pages/ArticleDetailPage.vue`
- Modify: `blog-frontend/src/features/articles/pages/ArticleDetailPage.test.ts`
- Modify: `blog-frontend/src/styles/public-pages.css`

**Interfaces:**
- Produces: ordered `attachmentMediaIds` in article writes and accessible public attachment list.
- Consumes: Task 5 DTOs and Task 6 uploader.

- [ ] **Step 1: Write failing admin editor tests**

Assert accepted extensions, size feedback, upload progress, attachment removal, order preservation, existing attachment hydration, draft save payload and prevention of save while an upload is active.

- [ ] **Step 2: Write failing article detail tests**

Assert the attachment section is absent when empty and otherwise renders name, formatted size, content type and stable public download link with accessible labels.

- [ ] **Step 3: Run and verify failure**

Run: `cd blog-frontend && npm run test:run -- src/features/articles/pages/AdminArticleEditorPage.test.ts src/features/articles/pages/ArticleDetailPage.test.ts`

Expected: attachment assertions fail.

- [ ] **Step 4: Implement attachment UI and contract mapping**

Use the shared uploader with purpose `ATTACHMENT`. Store media IDs and display metadata in component state; never persist provider URLs. Add keyboard-accessible move up/down and remove controls.

- [ ] **Step 5: Run focused tests and typecheck**

Run: `cd blog-frontend && npm run test:run -- src/features/articles/pages/AdminArticleEditorPage.test.ts src/features/articles/pages/ArticleDetailPage.test.ts && npm run typecheck`

Expected: focused tests and typecheck pass.

- [ ] **Step 6: Commit**

```bash
git add blog-frontend/src/features/articles blog-frontend/src/styles/public-pages.css
git commit -m "feat: add public article attachments"
```

### Task 8: Add Cloudflare R2 provider

**Files:**
- Modify: `blog-backend/pom.xml`
- Create: `blog-backend/src/main/java/com/blog/media/storage/r2/R2Properties.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/r2/R2Configuration.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/r2/R2ObjectStorage.java`
- Create: `blog-backend/src/test/java/com/blog/media/storage/r2/R2ObjectStorageTest.java`
- Modify: `blog-backend/src/main/resources/application.yml`
- Modify: `blog-backend/src/main/resources/application-prod.yml`

**Interfaces:**
- Produces: `StorageProvider.R2` DIRECT adapter using S3-compatible PutObject presigning, Head/Get/Delete and public-base URL resolution.
- Consumes: Task 2 contract and Task 4 state machine.

- [ ] **Step 1: Write failing R2 adapter tests**

Inject mocked `S3Client` and `S3Presigner`. Assert region `auto`, configured endpoint, ten-minute PUT signature, fixed bucket/key/content-type, HEAD mapping, validation stream retrieval, public URL path encoding, delete request and missing-object translation.

- [ ] **Step 2: Run and verify failure**

Run: `cd blog-backend && mvn -Dtest=R2ObjectStorageTest test`

Expected: compilation fails because AWS SDK dependency and R2 classes are absent.

- [ ] **Step 3: Add AWS SDK v2 dependencies and implement R2 configuration**

Add `software.amazon.awssdk:s3` using one pinned AWS SDK BOM version; `S3Presigner` is provided by that module. Validate HTTPS endpoint/public base URL, nonblank bucket and credentials only when `BLOG_MEDIA_PROVIDER=r2`. Use path-style access only if required by R2 endpoint behavior proven in the adapter test.

- [ ] **Step 4: Implement the R2 adapter**

Set object metadata for content type, immutable caching and attachment disposition. The public URL is `publicBaseUrl + encoded objectKey`; presigned URLs always use the R2 S3 API endpoint, never the custom public domain.

- [ ] **Step 5: Run focused and application tests**

Run: `cd blog-backend && mvn -Dtest=R2ObjectStorageTest,MediaApplicationServiceTest test`

Expected: all focused tests pass without real R2 credentials.

- [ ] **Step 6: Commit**

```bash
git add blog-backend/pom.xml blog-backend/src/main/java/com/blog/media/storage/r2 blog-backend/src/test/java/com/blog/media/storage/r2 blog-backend/src/main/resources/application.yml blog-backend/src/main/resources/application-prod.yml
git commit -m "feat: add Cloudflare R2 media storage"
```

### Task 9: Migrate cover/avatar callers and finish media administration

**Files:**
- Modify: `blog-backend/src/main/java/com/blog/article/ArticleService.java`
- Modify: `blog-backend/src/main/java/com/blog/tool/ToolService.java`
- Modify: `blog-backend/src/main/java/com/blog/topic/TopicService.java`
- Modify: `blog-backend/src/main/java/com/blog/site/SiteProfileService.java`
- Modify: `blog-backend/src/main/java/com/blog/media/AdminMediaController.java`
- Modify: `blog-backend/src/test/java/com/blog/article/ArticleServiceTest.java`
- Modify: `blog-backend/src/test/java/com/blog/tool/ToolServiceTest.java`
- Modify: `blog-backend/src/test/java/com/blog/topic/TopicServiceTest.java`
- Modify: `blog-backend/src/test/java/com/blog/site/SiteProfileControllerTest.java`
- Modify: `blog-frontend/src/features/media/pages/AdminMediaPage.vue`
- Modify: `blog-frontend/src/features/media/pages/AdminMediaPage.test.ts`
- Modify: cover/avatar upload callers under `blog-frontend/src/features/site`, `articles`, `topics`, and `tools`.

**Interfaces:**
- Produces: all media URLs use `/api/media/assets/{id}`; media list reports provider/status/purpose/reference state; deletion enforces reference safety.
- Consumes: Tasks 4-8.

- [ ] **Step 1: Write failing backend regression tests**

Require READY media with purpose compatible with each field, stable ID URLs in every response, and delete rejection for cover/avatar/topic/tool references.

- [ ] **Step 2: Write failing media-page and upload-caller tests**

Require provider/status/purpose/used badges, unused-only delete control, shared upload flow for all cover/avatar fields, and no call to the removed legacy admin upload endpoint.

- [ ] **Step 3: Run focused suites and verify failure**

Run backend service tests and `cd blog-frontend && npm run test:run -- src/features/media src/features/site src/features/articles/pages/AdminArticleEditorPage.test.ts src/features/topics/pages/AdminTopicPage.test.ts src/features/tools/pages/AdminToolEditorPage.test.ts`.

Expected: stable URL, purpose, media-page or shared-uploader assertions fail.

- [ ] **Step 4: Migrate callers and complete media administration**

Centralize stable URL construction in the media application layer. Remove legacy admin upload only after all frontend callers use the new uploader. Preserve old public storage-key reads.

- [ ] **Step 5: Run focused suites and typecheck**

Run: `cd blog-backend && mvn -Dtest=ArticleServiceTest,ToolServiceTest,TopicServiceTest,SiteProfileControllerTest,AdminMediaControllerTest test`

Run: `cd blog-frontend && npm run test:run -- src/features/media src/features/site src/features/articles/pages/AdminArticleEditorPage.test.ts src/features/topics/pages/AdminTopicPage.test.ts src/features/tools/pages/AdminToolEditorPage.test.ts && npm run typecheck`

Expected: all focused tests pass.

- [ ] **Step 6: Commit**

```bash
git add blog-backend/src/main/java/com/blog blog-backend/src/test/java/com/blog blog-frontend/src/features
git commit -m "refactor: route all media through media module"
```

### Task 10: Deployment configuration, documentation and release verification

**Files:**
- Modify: `.env.example`
- Modify: `.env.test.example`
- Modify: `docker-compose.yml`
- Modify: `README.md`
- Modify: `docs/deployment.md`
- Create: `docs/media-storage.md`
- Modify: `.github/workflows/ci.yml`
- Modify: `blog-frontend/e2e/admin-publish.spec.ts`

**Interfaces:**
- Produces: documented Local/R2 deployment, R2 CORS policy, secret handling, smoke checks and end-to-end Markdown image/attachment coverage.
- Consumes: all previous tasks.

- [ ] **Step 1: Extend release contract tests first**

Add assertions to the existing Docker preflight/static scripts for provider selection, required R2 variables only in R2 mode, no secret values in examples, persistent local media volume, and API-only credential exposure. Extend Playwright publish flow to upload a Markdown image and public attachment, save a draft, publish, and verify anonymous access.

- [ ] **Step 2: Run static/E2E discovery and verify failure**

Run: `scripts/tests/backup_restore_static_test.sh && ruby -e 'require "yaml"; YAML.load_file("docker-compose.yml")'`

Run: `cd blog-frontend && npm run test:e2e -- --list`

Expected: new environment/documentation assertions fail until configuration is added; Playwright lists the enhanced scenario.

- [ ] **Step 3: Add configuration and operator documentation**

Document bucket creation, scoped Object Read & Write token, S3 endpoint, custom public domain, development `r2.dev` caveat, exact CORS JSON, environment variables, key rotation, provider rollback and object migration. Keep Local as the default in development and require explicit R2 configuration in production when selected.

- [ ] **Step 4: Run full backend verification**

Run: `cd blog-backend && mvn test && mvn -DskipTests package`

Expected: 0 failures/errors; Docker-dependent tests follow the repository's established skip policy when Docker is unavailable; package succeeds.

- [ ] **Step 5: Run full frontend verification**

Run: `cd blog-frontend && npm run test:run && npm run typecheck && npm run build && npm run lint:legacy-routes && npm run test:nginx-template && npm run test:e2e -- --list`

Expected: all unit tests, typecheck, build and static checks pass; E2E tests are discovered.

- [ ] **Step 6: Run repository verification**

Run: `scripts/tests/manifest_contract_test.sh && scripts/tests/restore_safety_test.sh && scripts/tests/backup_restore_static_test.sh && git diff --check`

Expected: all scripts and whitespace checks pass.

- [ ] **Step 7: Commit**

```bash
git add .env.example .env.test.example docker-compose.yml README.md docs .github/workflows/ci.yml blog-frontend/e2e/admin-publish.spec.ts scripts
git commit -m "docs: configure pluggable media deployment"
```

- [ ] **Step 8: Manual acceptance with real R2 credentials**

With an uncommitted `.env` and a non-production test bucket, start the stack, upload via paste/drag/file picker, upload each attachment class, save and republish an article, verify anonymous redirects/downloads, verify unused-media behavior, then switch back to Local and verify legacy media. Record the observed HTTP status and object metadata; never print or commit credentials.
