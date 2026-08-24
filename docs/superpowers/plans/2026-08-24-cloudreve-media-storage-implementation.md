# Cloudreve Media Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a secure, OAuth-backed Cloudreve v4 storage provider to the blog while closing the four existing media release blockers and retaining Local/R2 compatibility.

**Architecture:** Cloudreve is a `PROXY` `ObjectStorage` adapter. The backend owns OAuth, streams files through Cloudreve's v4 upload/download APIs, and persists only stable provider-neutral locations; public visitors continue using blog media IDs. OAuth tokens are encrypted at rest, refreshed under a database lock, and never exposed to the frontend.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring Security, Spring Data JPA, Flyway/MySQL 8, Java `HttpClient` or Spring `RestClient`, Jackson, AES-GCM, Vue 3, TypeScript, Vitest, Testcontainers, Docker Compose.

**Spec:** `docs/superpowers/specs/2026-08-24-cloudreve-media-storage-design.md`

## Global Constraints

- Do not commit the supplied Cloudreve Client ID, Client Secret, OAuth tokens, encryption key, instance IP, or instance port.
- Every Cloudreve address, OAuth endpoint, callback URI, and root path must be configurable; no IP or port may appear in source defaults.
- Cloudreve is a proxy provider in v1; no browser receives a Cloudreve OAuth token.
- Network and Cloudreve file I/O must not run inside a database transaction.
- New uploads use the configured default provider; historical Local, R2, and Cloudreve objects remain readable by their persisted location.
- Public visitors never register or log in; only the administrator can authorize Cloudreve and upload/manage media.
- Keep public stable URLs `/api/media/assets/{id}` and `/api/media/assets/{id}/download` provider-neutral.
- Preserve the user-owned untracked `.gitignore` and runtime `blog-backend/media/` directory.
- Do not push to GitHub; the user will verify locally before any push.

---

### Task 1: Make the existing media foundation safe for Cloudreve

**Files:**
- Modify: `blog-backend/src/main/resources/db/migration/V9__add_pluggable_media_storage.sql`
- Create: `blog-backend/src/main/resources/db/migration/V13__harden_media_location_identity.sql`
- Modify: `blog-backend/src/main/java/com/blog/media/storage/r2/R2ObjectStorage.java`
- Modify: `blog-backend/src/main/java/com/blog/media/MediaApplicationService.java`
- Modify: `blog-backend/src/main/java/com/blog/media/PublicMediaController.java`
- Test: `blog-backend/src/test/java/com/blog/media/MediaMigrationMySqlIntegrationTest.java`
- Test: `blog-backend/src/test/java/com/blog/media/storage/r2/R2ObjectStorageTest.java`
- Test: `blog-backend/src/test/java/com/blog/media/PublicMediaControllerTest.java`
- Modify: `docs/media-storage.md`

**Interfaces:**
- Consumes: `ObjectLocation`, `ObjectStorageException`, existing stable public media endpoints.
- Produces: a portable media location unique constraint; conditional R2 create-only uploads; normalized 404/503 behavior for inline media.

- [ ] **Step 1: Write failing MySQL migration tests**

Add Testcontainers tests that migrate a fresh empty MySQL 8 database through V13 and an upgrade fixture representing V8 data. Query `information_schema.statistics` and assert the final unique identity does not depend on a `utf8mb4` key wider than InnoDB's 3072-byte limit.

```java
assertThat(indexColumns("media_asset", "uk_media_location_hash"))
        .containsExactly("location_hash");
assertThat(migrationSucceeded()).isTrue();
```

- [ ] **Step 2: Run the migration test and verify RED**

Run: `cd blog-backend && mvn -Dtest=MediaMigrationMySqlIntegrationTest test`

Expected: FAIL on the current V9 composite index or because V13/location hash does not exist. If Docker is unavailable, record the skip as an external gate and still run a static DDL contract test that calculates the declared maximum index width.

- [ ] **Step 3: Implement a portable location identity**

Change V9's bootstrap index to a safe prefix index so a fresh database can reach later migrations, then make V13 replace it with a stored SHA-256 location identity:

```sql
ALTER TABLE media_asset
    ADD COLUMN location_hash BINARY(32)
      GENERATED ALWAYS AS (
        UNHEX(SHA2(CONCAT(provider, CHAR(0), bucket, CHAR(0), storage_key), 256))
      ) STORED,
    DROP INDEX uk_media_asset_location,
    ADD UNIQUE KEY uk_media_location_hash (location_hash);
```

Document that installations which previously recorded the old V9 checksum must back up the database, deploy the reviewed V9/V13 pair, run `flyway repair` once, and then migrate. Add a script/static contract that rejects a future oversized composite index.

- [ ] **Step 4: Write failing R2 replay and public error tests**

Assert that the signed PUT requires `If-None-Match: *`, the returned upload plan exposes that exact required header, and replay receives a provider conflict rather than overwriting. Assert inline media returns sanitized 404 for missing objects and 503 for missing adapter/bucket configuration.

```java
assertThat(ticket.requiredHeaders()).containsEntry("If-None-Match", "*");
mockMvc.perform(get("/api/media/assets/{id}", mediaId))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.detail").value("Media storage is temporarily unavailable"));
```

- [ ] **Step 5: Implement conditional R2 uploads and normalized public errors**

Add the create-only condition to both `PutObjectRequest` and the returned signed headers. Update documented R2 CORS allowed headers. Wrap `resolvePublic` provider lookup/location resolution with the same sanitized NOT_FOUND/TRANSIENT mapping used by public downloads; never return provider, bucket, key, or internal URI in Problem Details.

- [ ] **Step 6: Run focused and full backend verification**

Run:

```bash
cd blog-backend
mvn -Dtest=MediaMigrationMySqlIntegrationTest,R2ObjectStorageTest,PublicMediaControllerTest test
mvn test
```

Expected: focused tests pass; full suite passes, with Docker tests explicitly reported if skipped.

- [ ] **Step 7: Commit**

```bash
git add blog-backend/src/main/resources/db/migration/V9__add_pluggable_media_storage.sql blog-backend/src/main/resources/db/migration/V13__harden_media_location_identity.sql blog-backend/src/main/java/com/blog/media/storage/r2/R2ObjectStorage.java blog-backend/src/main/java/com/blog/media/MediaApplicationService.java blog-backend/src/main/java/com/blog/media/PublicMediaController.java blog-backend/src/test/java/com/blog/media/MediaMigrationMySqlIntegrationTest.java blog-backend/src/test/java/com/blog/media/storage/r2/R2ObjectStorageTest.java blog-backend/src/test/java/com/blog/media/PublicMediaControllerTest.java docs/media-storage.md
git commit -m "fix: close media release blockers"
```

### Task 2: Protect media referenced from tool Markdown

**Files:**
- Create: `blog-backend/src/main/resources/db/migration/V14__add_tool_media_references.sql`
- Create: `blog-backend/src/main/java/com/blog/media/ToolMedia.java`
- Create: `blog-backend/src/main/java/com/blog/media/ToolMediaId.java`
- Create: `blog-backend/src/main/java/com/blog/media/ToolMediaRepository.java`
- Create: `blog-backend/src/main/java/com/blog/media/ToolMediaReferenceService.java`
- Modify: `blog-backend/src/main/java/com/blog/media/ArticleMediaReferenceService.java`
- Modify: `blog-backend/src/main/java/com/blog/media/MediaReferenceChecker.java`
- Modify: `blog-backend/src/main/java/com/blog/tool/ToolService.java`
- Test: `blog-backend/src/test/java/com/blog/media/ToolMediaReferenceServiceTest.java`
- Test: `blog-backend/src/test/java/com/blog/tool/ToolServiceTest.java`
- Test: `blog-backend/src/test/java/com/blog/media/MediaReferenceCheckerTest.java`

**Interfaces:**
- Consumes: stable media URL parser behavior and media row-locking from the existing article reference service.
- Produces: `ToolMediaReferenceService.synchronize(Tool tool, String markdown)` and `removeAll(Tool tool)`; bulk tool-body reference lookup for deletion protection.

- [ ] **Step 1: Extract and test a shared stable-media Markdown parser**

Move the CommonMark image-node extraction into a focused `StableMediaReferenceParser` used by articles and tools. Test fenced code exclusion, duplicate IDs, query/fragment normalization policy, and rejection of foreign URLs.

```java
assertThat(parser.parse("![x](/api/media/assets/42)\n```md\n![x](/api/media/assets/99)\n```"))
        .containsExactly(42L);
```

- [ ] **Step 2: Run parser tests and verify RED**

Run: `cd blog-backend && mvn -Dtest=StableMediaReferenceParserTest test`

Expected: FAIL because the shared parser does not exist.

- [ ] **Step 3: Add normalized tool-media persistence**

Create `tool_media(tool_id, media_id, sort_order)` with composite primary key and foreign keys that cascade only from tool deletion. Do not cascade from media deletion. Implement differential synchronization: validate and lock all referenced READY `INLINE_IMAGE` media in ascending ID order before changing rows; retain unchanged composite IDs instead of delete-then-merge.

- [ ] **Step 4: Integrate tool create/update/delete**

Call `synchronize` inside the existing tool write transaction after validating the request and before commit. On tool deletion call `removeAll`. Extend `MediaReferenceChecker` single and bulk checks to include `tool_media`.

- [ ] **Step 5: Add service and persistence tests**

Cover create, retain, reorder, remove, invalid purpose/status, rollback, concurrent media deletion locking, and deletion protection. Add a MySQL integration test retaining an unchanged image through a tool update.

- [ ] **Step 6: Run focused and full tests**

Run:

```bash
cd blog-backend
mvn -Dtest=ToolMediaReferenceServiceTest,ToolServiceTest,MediaReferenceCheckerTest test
mvn test
```

Expected: all non-Docker tests pass; MySQL integration passes when Docker is available.

- [ ] **Step 7: Commit**

```bash
git add blog-backend/src/main/resources/db/migration/V14__add_tool_media_references.sql blog-backend/src/main/java/com/blog/media blog-backend/src/main/java/com/blog/tool/ToolService.java blog-backend/src/test/java/com/blog/media blog-backend/src/test/java/com/blog/tool/ToolServiceTest.java
git commit -m "feat: protect tool Markdown media"
```

### Task 3: Add Cloudreve configuration and OAuth persistence

**Files:**
- Modify: `blog-backend/src/main/java/com/blog/media/StorageProvider.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveProperties.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveConfiguration.java`
- Create: `blog-backend/src/main/resources/db/migration/V15__add_cloudreve_oauth_connection.sql`
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveConnection.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveConnectionRepository.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveConnectionStatus.java`
- Modify: `blog-backend/src/main/resources/application.yml`
- Test: `blog-backend/src/test/java/com/blog/media/storage/cloudreve/CloudrevePropertiesTest.java`
- Test: `blog-backend/src/test/java/com/blog/media/storage/cloudreve/CloudreveConnectionMappingTest.java`

**Interfaces:**
- Consumes: Spring configuration binding and `StorageProvider` persistence as strings.
- Produces: validated `CloudreveProperties`; one encrypted OAuth connection record; conditional Cloudreve beans independent of the default upload provider.

- [ ] **Step 1: Write failing configuration tests**

Test base-URL-derived defaults, explicit endpoint overrides, trusted internal HTTP opt-in, URI rejection, normalized `/blog` root, absent-secret behavior in Local mode, and required configuration when Cloudreve is enabled/readable.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd blog-backend && mvn -Dtest=CloudrevePropertiesTest,CloudreveConnectionMappingTest test`

Expected: FAIL because Cloudreve types and V15 mapping do not exist.

- [ ] **Step 3: Implement properties and conditional registration**

Use typed `URI`, `Duration`, and normalized path fields. Derive endpoints only when their explicit property is blank:

```java
URI authorizationUri() { return overrideOrResolve(authorizationUri, "/session/authorize"); }
URI tokenUri() { return overrideOrResolve(tokenUri, "/api/v4/session/oauth/token"); }
URI refreshUri() { return overrideOrResolve(refreshUri, "/api/v4/session/token/refresh"); }
URI userInfoUri() { return overrideOrResolve(userInfoUri, "/api/v4/session/oauth/userinfo"); }
```

Do not place concrete network values in defaults.

- [ ] **Step 4: Add OAuth connection migration/entity**

Store encrypted token bytes, nonces, expiry instants, granted scopes, authorized subject/name, status, version, and timestamps. Enforce a singleton logical key. Never store Client Secret or the Token encryption key.

- [ ] **Step 5: Run focused and full tests**

Run: `cd blog-backend && mvn -Dtest=CloudrevePropertiesTest,CloudreveConnectionMappingTest test && mvn test`

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add blog-backend/src/main/java/com/blog/media/StorageProvider.java blog-backend/src/main/java/com/blog/media/storage/cloudreve blog-backend/src/main/resources/application.yml blog-backend/src/main/resources/db/migration/V15__add_cloudreve_oauth_connection.sql blog-backend/src/test/java/com/blog/media/storage/cloudreve
git commit -m "feat: define Cloudreve provider configuration"
```

### Task 4: Implement OAuth cryptography and token lifecycle

**Files:**
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveTokenCipher.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveOAuthClient.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveOAuthTransaction.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveOAuthTransactionRepository.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveTokenService.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveAuthorizationRequiredException.java`
- Test: `blog-backend/src/test/java/com/blog/media/storage/cloudreve/CloudreveTokenCipherTest.java`
- Test: `blog-backend/src/test/java/com/blog/media/storage/cloudreve/CloudreveOAuthClientTest.java`
- Test: `blog-backend/src/test/java/com/blog/media/storage/cloudreve/CloudreveTokenServiceTest.java`

**Interfaces:**
- Consumes: `CloudreveProperties`, OAuth connection repository.
- Produces: `URI beginAuthorization(long adminId)`; `void completeAuthorization(code,state,adminId)`; `String validAccessToken()`; `disconnect(long adminId)`.

- [ ] **Step 1: Write AES-GCM and PKCE tests**

Verify random nonces produce different ciphertext, correct additional authenticated data is mandatory, tampering fails closed, no secret appears in `toString`, state is one-time, and PKCE uses S256 base64url without padding.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd blog-backend && mvn -Dtest=CloudreveTokenCipherTest,CloudreveOAuthClientTest,CloudreveTokenServiceTest test`

Expected: FAIL because lifecycle classes are absent.

- [ ] **Step 3: Implement OAuth HTTP contracts**

Use form encoding for authorization-code exchange and JSON for refresh, matching Cloudreve v4. Require `openid profile offline_access Files.Write`; treat returned scopes as authoritative and reject a connection missing `Files.Write` or `offline_access`. Bound response bodies and timeouts. Redact Authorization headers and response bodies from logs.

- [ ] **Step 4: Implement encrypted token persistence and refresh locking**

Use AES-GCM with a versioned key format. Refresh under a pessimistic row lock in a short transaction, but perform HTTP refresh outside the transaction by using a token claim/version protocol. Atomically write the new access/refresh pair only if the version still matches; a competing refresh reads the winner. Mark `REAUTH_REQUIRED` only for an OAuth invalid-grant response, not for transient network/5xx failures.

- [ ] **Step 5: Add concurrency and failure tests**

Cover two concurrent callers, refresh-token rotation, invalid grant, Cloudreve timeout, token exchange replay, state expiry, wrong admin, callback error, encryption key mismatch, and reauthorization replacing an old connection.

- [ ] **Step 6: Run tests**

Run: `cd blog-backend && mvn -Dtest=CloudreveTokenCipherTest,CloudreveOAuthClientTest,CloudreveTokenServiceTest test && mvn test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add blog-backend/src/main/java/com/blog/media/storage/cloudreve blog-backend/src/test/java/com/blog/media/storage/cloudreve
git commit -m "feat: implement Cloudreve OAuth lifecycle"
```

### Task 5: Expose administrator Cloudreve connection APIs

**Files:**
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/AdminCloudreveController.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveOAuthCallbackController.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/dto/CloudreveConnectionResponse.java`
- Modify: `blog-backend/src/main/java/com/blog/config/SecurityConfig.java`
- Modify: `blog-backend/src/main/java/com/blog/shared/error/GlobalExceptionHandler.java`
- Test: `blog-backend/src/test/java/com/blog/media/storage/cloudreve/AdminCloudreveControllerTest.java`
- Test: `blog-backend/src/test/java/com/blog/media/storage/cloudreve/CloudreveOAuthCallbackControllerTest.java`

**Interfaces:**
- Consumes: OAuth token service from Task 4.
- Produces: `GET /api/admin/media/cloudreve`, `POST /api/admin/media/cloudreve/authorize`, `POST /api/admin/media/cloudreve/disconnect`, and configured callback endpoint.

- [ ] **Step 1: Write controller security tests**

Require admin session and CSRF for status-changing endpoints. Verify authorization returns only a redirect URL; callback requires the original admin session/state; responses never contain tokens, Client Secret, verifier, internal exception bodies, or encryption material.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd blog-backend && mvn -Dtest=AdminCloudreveControllerTest,CloudreveOAuthCallbackControllerTest test`

Expected: FAIL because endpoints do not exist.

- [ ] **Step 3: Implement controllers and error responses**

Return a DTO containing only `configured`, `status`, authorized display identity, scopes, access expiry, refresh expiry, and root path. Callback redirects to `/admin/settings?cloudreve=connected` or a fixed error code; never reflect arbitrary Cloudreve error text.

- [ ] **Step 4: Run focused and full tests**

Run: `cd blog-backend && mvn -Dtest=AdminCloudreveControllerTest,CloudreveOAuthCallbackControllerTest test && mvn test`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add blog-backend/src/main/java/com/blog/media/storage/cloudreve blog-backend/src/main/java/com/blog/config/SecurityConfig.java blog-backend/src/main/java/com/blog/shared/error/GlobalExceptionHandler.java blog-backend/src/test/java/com/blog/media/storage/cloudreve
git commit -m "feat: expose Cloudreve OAuth administration"
```

### Task 6: Implement the Cloudreve v4 file client

**Files:**
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveFileClient.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveUploadSession.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveFileMetadata.java`
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveApiException.java`
- Test: `blog-backend/src/test/java/com/blog/media/storage/cloudreve/CloudreveFileClientTest.java`

**Interfaces:**
- Consumes: `CloudreveTokenService.validAccessToken()` and typed Cloudreve endpoints.
- Produces: `upload(path, request, InputStream)`, `inspect(path)`, `open(path)`, and `delete(path)` with provider-neutral error categories.

- [ ] **Step 1: Pin the Cloudreve v4 wire contract in tests**

Use a local mock HTTP server. Assert exact methods, paths, Authorization headers, request JSON/form fields, chunk indices, completion callback, bounded response handling, and token-refresh retry once after 401. Save representative response fixtures under test resources without real instance data.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd blog-backend && mvn -Dtest=CloudreveFileClientTest test`

Expected: FAIL because the client is absent.

- [ ] **Step 3: Implement directory and upload-session operations**

Resolve the configured root through Cloudreve URI semantics, create missing purpose/date directories idempotently, request an upload session, and validate the returned session ID, chunk size, URL/credential and expiry. Reject redirects to an origin outside the configured Cloudreve API or a provider upload allowlist.

- [ ] **Step 4: Implement bounded streaming chunks**

Read at most the Cloudreve chunk size per request using a reusable bounded buffer, never the whole asset. Track bytes against `ObjectUploadRequest.maxBytes()`. Abort the Cloudreve session after partial failure where supported. Do not retry a chunk unless the protocol proves the same chunk operation is idempotent.

- [ ] **Step 5: Implement metadata, content and delete operations**

Map 401 to one token refresh/retry, 404 to NOT_FOUND, 409 to conflict, 429/5xx/timeouts to TRANSIENT, and malformed successful responses to provider failure. Return an `InputStream` whose close action also closes the HTTP response.

- [ ] **Step 6: Run focused and full tests**

Run: `cd blog-backend && mvn -Dtest=CloudreveFileClientTest test && mvn test`

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add blog-backend/src/main/java/com/blog/media/storage/cloudreve blog-backend/src/test/java/com/blog/media/storage/cloudreve
git commit -m "feat: add Cloudreve file API client"
```

### Task 7: Add CloudreveObjectStorage and media lifecycle integration

**Files:**
- Create: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveObjectStorage.java`
- Modify: `blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveConfiguration.java`
- Modify: `blog-backend/src/main/java/com/blog/media/MediaApplicationService.java`
- Modify: `blog-backend/src/main/java/com/blog/media/MediaOperationTransactionService.java`
- Test: `blog-backend/src/test/java/com/blog/media/storage/cloudreve/CloudreveObjectStorageTest.java`
- Test: `blog-backend/src/test/java/com/blog/media/MediaApplicationServiceTest.java`
- Test: `blog-backend/src/test/java/com/blog/media/MediaOperationConcurrencyMySqlIntegrationTest.java`

**Interfaces:**
- Consumes: Cloudreve file client and current recoverable media lifecycle.
- Produces: `StorageProvider.CLOUDREVE` adapter with `PROXY` upload, authoritative inspection, streaming read, and idempotent delete.

- [ ] **Step 1: Write failing adapter contract tests**

Test normalized root containment, generated immutable paths, proxy-only capabilities, stream delegation, metadata mapping, missing/transient mapping, idempotent delete, no network I/O inside Spring transactions, and readable Cloudreve rows when the default provider is Local/R2.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd blog-backend && mvn -Dtest=CloudreveObjectStorageTest,MediaApplicationServiceTest test`

Expected: FAIL because the adapter is absent.

- [ ] **Step 3: Implement the adapter**

Return `new StorageCapabilities(false, true)`. `locationForNewObject` combines configured root identity with the server-owned purpose/date/UUID path. `upload`, `inspect`, `openStream`, and `delete` delegate to the file client and preserve provider-neutral semantics. `resolvePublicUrl` must not return Cloudreve internal or temporary URLs; Cloudreve assets use proxy public access.

- [ ] **Step 4: Make public image handling capability-aware**

Extend the storage capability/public media response so DIRECT-public providers may redirect while proxy-public providers stream. Keep DB snapshot reads short and open Cloudreve streams outside transactions. Preserve Local legacy routes and R2 redirect behavior.

- [ ] **Step 5: Add crash/concurrency regression tests**

Cover upload success with DB failure, complete versus cleanup, delete retry, expired claims, Token reauthorization during a request, and a historical Cloudreve asset under a non-Cloudreve default provider.

- [ ] **Step 6: Run focused and full backend tests**

Run:

```bash
cd blog-backend
mvn -Dtest=CloudreveObjectStorageTest,MediaApplicationServiceTest,MediaOperationConcurrencyMySqlIntegrationTest test
mvn test
mvn -DskipTests package
```

Expected: unit tests and package pass; MySQL concurrency gate passes with Docker or is reported as an external block.

- [ ] **Step 7: Commit**

```bash
git add blog-backend/src/main/java/com/blog/media/storage/cloudreve blog-backend/src/main/java/com/blog/media/MediaApplicationService.java blog-backend/src/main/java/com/blog/media/MediaOperationTransactionService.java blog-backend/src/test/java/com/blog/media/storage/cloudreve blog-backend/src/test/java/com/blog/media
git commit -m "feat: integrate Cloudreve media storage"
```

### Task 8: Add the Cloudreve connection UI

**Files:**
- Modify: `blog-frontend/src/shared/api/contracts.ts`
- Modify: `blog-frontend/src/features/media/api.ts`
- Create: `blog-frontend/src/features/media/components/CloudreveConnectionCard.vue`
- Create: `blog-frontend/src/features/media/components/CloudreveConnectionCard.test.ts`
- Modify: `blog-frontend/src/features/site/pages/AdminSettingsPage.vue`
- Modify: `blog-frontend/src/features/site/pages/AdminSettingsPage.test.ts`

**Interfaces:**
- Consumes: Task 5 administrator Cloudreve APIs.
- Produces: accessible connect, reconnect and disconnect UI without exposing secret/token fields.

- [ ] **Step 1: Write failing UI tests**

Test disconnected/config-missing/connected/reauthorization states, loading and error feedback, OAuth redirect navigation, confirmation before disconnect, callback query feedback, and absence of token/secret input or response rendering.

- [ ] **Step 2: Run tests and verify RED**

Run: `cd blog-frontend && npm run test:run -- CloudreveConnectionCard AdminSettingsPage`

Expected: FAIL because the card and API contracts are absent.

- [ ] **Step 3: Implement typed API mapping**

Add snake_case decoding for connection status and expiry fields. `authorizeCloudreve()` returns only an HTTPS/allowed internal HTTP absolute authorization URL; reject other schemes before navigation.

- [ ] **Step 4: Implement the connection card**

Render status, authorized user, scope, expiry and root path. Use buttons for connection actions, `aria-live` for results, and fixed Chinese error copy. Never render Client ID, Client Secret, access token, refresh token, verifier, state, or internal error bodies.

- [ ] **Step 5: Run frontend verification**

Run:

```bash
cd blog-frontend
npm run test:run
npm run typecheck
npm run build
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add blog-frontend/src/shared/api/contracts.ts blog-frontend/src/features/media blog-frontend/src/features/site/pages/AdminSettingsPage.vue blog-frontend/src/features/site/pages/AdminSettingsPage.test.ts
git commit -m "feat: manage Cloudreve connection in admin"
```

### Task 9: Wire deployment configuration and operator documentation

**Files:**
- Modify: `docker-compose.yml`
- Modify: `.env.example`
- Modify: `README.md`
- Modify: `docs/media-storage.md`
- Create: `docs/cloudreve-media.md`
- Modify: `scripts/tests/backup_restore_static_test.sh`
- Test: `blog-frontend/scripts/test-nginx-template.mjs`

**Interfaces:**
- Consumes: all Cloudreve configuration and operational behavior.
- Produces: secret-safe Local/R2/Cloudreve deployment examples, OAuth setup/runbook, migration and rollback instructions.

- [ ] **Step 1: Write failing deployment contract checks**

Assert Cloudreve secrets enter only the API container, network locations are variables, no concrete instance IP/port or supplied credential appears in tracked files, Local remains default, and OAuth callback/config variables survive Compose interpolation.

- [ ] **Step 2: Run static checks and verify RED**

Run:

```bash
bash scripts/tests/backup_restore_static_test.sh
cd blog-frontend && npm run test:nginx-template
```

Expected: FAIL because Cloudreve contracts are not wired.

- [ ] **Step 3: Wire Compose and environment examples**

Pass Cloudreve variables only to `api`. Do not give them to `web`, frontend build args, health endpoints or command-line logs. Keep all values blank/example placeholders and document trusted internal HTTP as an explicit development-only opt-in.

- [ ] **Step 4: Write the operator runbook**

Document Cloudreve OAuth app creation, configurable endpoints, callback registration, minimum scopes, Token encryption-key generation/backup, connect/reconnect flow, root directory permissions, secret rotation, provider switching, failure diagnosis, uninstall behavior, and why the test Client Secret must be rotated before production.

- [ ] **Step 5: Run complete release verification**

Run:

```bash
cd blog-backend && mvn test && mvn -DskipTests package
cd ../blog-frontend && npm run test:run && npm run typecheck && npm run build && npm run lint:legacy-routes && npm run test:nginx-template
cd .. && bash scripts/tests/backup_restore_static_test.sh && git diff --check
```

Expected: all available checks pass. Record Docker/Testcontainers skips without describing them as passes.

- [ ] **Step 6: Perform credential and concrete-host scan**

Run a tracked-file scan for the supplied Client ID/Secret, `access_token`, `refresh_token`, private IP literals and `.env` secret assignments. Verify any matches are variable names, DTO fixture keys with dummy values, or documentation warnings only.

- [ ] **Step 7: Commit**

```bash
git add docker-compose.yml .env.example README.md docs/media-storage.md docs/cloudreve-media.md scripts/tests/backup_restore_static_test.sh blog-frontend/scripts/test-nginx-template.mjs
git commit -m "docs: configure Cloudreve media deployment"
```

### Task 10: Validate against the real Cloudreve instance

**Files:**
- Create outside Git: deployment `.env` containing the temporary test Client ID/Secret and a random Token encryption key.
- Create ignored acceptance record: `.superpowers/sdd/2026-08-24-cloudreve-media-storage/real-cloudreve-acceptance.md`

**Interfaces:**
- Consumes: complete Cloudreve provider and the user's reachable instance.
- Produces: evidence for OAuth, upload, anonymous read, refresh and delete; a precise list of any missing scopes or instance settings.

- [ ] **Step 1: Start the application with untracked secrets**

Inject the supplied test credentials only through process environment or an ignored deployment `.env`. Confirm `git status --short` cannot expose the file and logs do not print secret values.

- [ ] **Step 2: Complete OAuth authorization**

From the admin settings page, connect Cloudreve and verify the callback returns to the blog. Confirm the authorized identity and required scopes. If Cloudreve requires a read scope beyond `Files.Write`, stop and report the exact scope before changing the OAuth application.

- [ ] **Step 3: Exercise real file operations**

Upload a PNG and a small public attachment, complete them, save/publish content, verify anonymous image and attachment access, inspect the configured `/blog` directory in Cloudreve, then delete an unused test asset from the media library.

- [ ] **Step 4: Exercise failure and refresh behavior**

Temporarily use an expired access token or wait for refresh, verify automatic refresh, interrupt Cloudreve connectivity and verify 503/retry without deletion, then restore connectivity and complete successfully.

- [ ] **Step 5: Run the final whole-branch review**

Generate a frozen diff from plan base to HEAD and perform independent spec-compliance, security, transaction, OAuth, storage and deployment review. Any Critical/Important finding enters a bounded fix/re-review loop before release approval.

- [ ] **Step 6: Record the external gate result**

Record timestamps, HTTP status classes, media IDs and sanitized Cloudreve paths. Do not record authorization codes, Tokens, Client Secret, signed URLs or response bodies containing credentials.
