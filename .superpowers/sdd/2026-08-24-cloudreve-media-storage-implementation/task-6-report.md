# Task 6 Report: Cloudreve v4 file API client

## Delivered

- Added a typed Cloudreve v4 file client with `upload`, `inspect`, `open`, and `delete` operations and provider-neutral `NOT_FOUND`, `CONFLICT`, `TRANSIENT`, and `PROVIDER_FAILURE` categories.
- Implemented normalized `cloudreve://my/...` path construction below the configured root, idempotent parent-folder creation, upload-session creation and validation, zero-based local/relay and remote chunks, and S3/KS3 multipart completion plus the policy-specific callback.
- Streams uploads with one reusable buffer capped at 32 MiB and verifies the declared length without buffering the whole object. Partial failures make one best-effort upload-session abort and non-idempotent chunks are never retried.
- Added bounded 64 KiB API/provider response bodies, complete request timeouts, manual download redirects limited to three same-allowlist hops, fail-closed upload redirects, explicit provider-origin allowlisting, and close-propagating content streams.
- Added a race-safe `CloudreveTokenService.validAccessTokenAfterRejection` hook. A 401 invalidates only the exact rejected cached token, obtains a refreshed/replacement token once, and cannot overwrite a token refreshed concurrently.
- Kept callback and presigned provider requests free of the OAuth bearer token. Errors do not surface provider response bodies, credentials, session secrets, internal URLs, or token values.

## Wire-contract evidence

- Cloudreve's official v4 upload guide defines session creation, zero-based local/relay chunks, remote `?chunk=` uploads with the returned credential, S3-compatible presigned part uploads, and policy-specific completion: <https://github.com/cloudreve/docs/blob/v4/en/api/upload.md>.
- Cloudreve's official web uploader confirms `application/octet-stream` part/completion bodies, raw quoted ETags in `CompleteMultipartUpload`, and distinct S3/KS3 callback policy paths: <https://github.com/cloudreve/frontend/blob/master/src/component/Uploader/core/api/index.ts>, <https://github.com/cloudreve/frontend/blob/master/src/component/Uploader/core/uploader/s3.ts>, and <https://github.com/cloudreve/frontend/blob/master/src/component/Uploader/core/uploader/ks3.ts>.
- Cloudreve's official router source confirms `PUT/POST/DELETE /api/v4/file/upload...` and unauthenticated provider callback routes: <https://github.com/cloudreve/Cloudreve/blob/master/routers/router.go>.
- No supplied Cloudreve instance or credential was used; all HTTP assertions use ephemeral loopback servers and synthetic fixture values.

## TDD and verification

- RED: `CloudreveFileClientTest` initially failed compilation because the client and exception types were absent.
- RED follow-ups pinned wrong-session URI rejection, callback authentication, disabled/unconfigured Spring creation, non-idempotent logical-401 handling, token-service exception normalization, and one-time rejected-token refresh before their implementations.
- Clean focused suite passed: `mvn clean -Dtest=CloudreveFileClientTest,CloudreveTokenServiceTest test` — 43 tests, 0 failures, 0 errors, 0 skipped.
- Full backend suite passed: `mvn test` — 448 tests, 0 failures, 0 errors, 35 skipped.
- `git diff --check` passed.

## Scope and concerns

- The 35 skipped full-suite tests are pre-existing Docker-dependent integration tests; Docker was unavailable in this environment.
- Review fix round 1 below narrows deployment acceptance to the configured S3 policy ID. Local/remote transport helpers remain protocol-correct, but Local, Remote, KS3, and other returned policy types now fail closed before transfer.
- External presigned upload and download origins must be listed in `blog.media.cloudreve.provider-origins`; HTTPS is required unless trusted internal HTTP is explicitly enabled.
- Pre-existing untracked `.gitignore` and `blog-backend/media/` were preserved and excluded from the Task 6 commit.

## Review fix round 1

### Protocol and policy hardening

- `chunk_size=0` now follows Cloudreve's official unpartitioned-upload meaning: one exact-length request is streamed without allocating the whole declared object. This covers local-style relay transport (including the approved S3 policy when `relay=true`), empty objects, and the protocol helpers for local/remote sessions.
- Every missing ancestor, including a fresh configured root, is created in order with `err_on_conflict=false`; HTTP 409 and Cloudreve logical 40004 are accepted so retries remain idempotent.
- Upload-session parsing preserves a recoverable session ID and aborts malformed sessions. Five-digit Cloudreve server codes in the 50000 range map to `TRANSIENT`, authenticated API calls retry once after either HTTP or logical 401, and representative S3 multipart tests use the provider-valid 5 MiB non-final part contract.
- S3 `CompleteMultipartUpload` 2xx bodies are parsed as hardened XML. Embedded/root `Error` responses and malformed/incomplete success documents are rejected and the session is aborted before the Cloudreve callback.
- Download bodies are back-pressured instead of whole-buffered, capped at the largest configured media limit, and governed by an absolute deadline that starts after response headers. Limit/deadline/close failures cancel the live HTTP subscription.
- The upload maximum is independent of provider chunk sizing. It is carried from the purpose/content-type application policy in `ObjectUploadRequest.maxBytes()`; declared oversize uploads fail before network I/O, while a small final part remains valid when Cloudreve advertises a larger provider chunk.

### Deployment acceptance ruling and cost

- Active Cloudreve configuration now requires `blog.media.cloudreve.policy-id` (`BLOG_MEDIA_CLOUDREVE_POLICY_ID`). Session creation sends `policy_id`, and the returned policy must have the exact configured ID and type `s3` before any asset bytes are transferred.
- A matching S3 policy with `relay=true` is accepted even though its transport uses Cloudreve's local-style chunk endpoint; a matching non-relay S3 policy uses multipart. Returned Local, Remote, KS3, any other type, or a mismatched ID is rejected before data transfer. Local/remote zero-chunk helpers remain protocol-correct but are deliberately not accepted end-to-end for this deployment.
- This fail-closed pin is the Cloudflare/R2 trust boundary: recreating or changing the approved Cloudreve policy changes its ID and stops uploads until operators update the configured ID. The client cannot independently infer the external vendor from Cloudreve's session shape; the configured policy ID supplies that administrative assertion while returned type `s3` prevents policy-type drift.

### Source and TDD evidence

- Cloudreve's upload guide documents unpartitioned `chunk_size=0`, local/relay and remote transports, and S3-compatible multipart completion: <https://github.com/cloudreve/docs/blob/v4/en/api/upload.md>. The backend upload service treats `chunk_size == 0` as the final whole-object chunk and accepts `policy_id`: <https://github.com/cloudreve/Cloudreve/blob/master/service/explorer/upload.go>. Official serializer documentation identifies five-digit codes beginning with 5 as server-side failures: <https://pkg.go.dev/github.com/cloudreve/Cloudreve/v4/pkg/serializer>.
- RED: a 1 MiB+17 zero-chunk relay fixture proved the resumed implementation requested the whole object in one caller-stream read (`1048593 > 65536`). RED: a one-byte upload with an independent 10-byte application maximum and a 32 MiB provider chunk was incorrectly rejected as a malformed session.
- GREEN: the zero-chunk path now streams through an exact-length publisher whose largest caller-stream read is at most 64 KiB, and session validation no longer treats provider chunk size as the application maximum. Additional focused cases cover policy request/response pinning, pre-transfer drift rejection, recursive directory creation, S3 2xx error XML, recoverable malformed-session abort, five-digit errors, HTTP 401, bounded/deadline downloads, and application-layer maximum propagation.
- Focused command: `mvn -Dtest=CloudreveFileClientTest,CloudrevePropertiesTest,AdminCloudreveEnabledValuesControllerTest,MediaApplicationServiceTest test` — 78 tests, 0 failures, 0 errors, 0 skipped.
- Full command: `mvn test` — 465 tests, 0 failures, 0 errors, 35 skipped. The skipped tests remain Docker-dependent integration gates; Docker is unavailable in this environment.
- `git diff --check` passed. No concrete Cloudreve instance value or credential was used; tests use synthetic identifiers and ephemeral loopback servers only.
