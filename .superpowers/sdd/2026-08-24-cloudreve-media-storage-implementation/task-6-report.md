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
- Direct non-relay uploads are intentionally implemented for the approved deployment's S3-compatible Cloudflare R2 policy (plus KS3), as well as Cloudreve local/relay and remote policies. Non-relay OneDrive, OSS, COS, OBS, Qiniu, and Upyun sessions fail closed as unsupported rather than guessing provider-specific completion semantics.
- External presigned upload and download origins must be listed in `blog.media.cloudreve.provider-origins`; HTTPS is required unless trusted internal HTTP is explicitly enabled.
- Pre-existing untracked `.gitignore` and `blog-backend/media/` were preserved and excluded from the Task 6 commit.
