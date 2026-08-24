# Final whole-branch fix round 4 report

## Implemented

- Added token-aware recovery for uncertain completion finalization. If the final `VERIFYING -> READY` transaction fails, the application runs a new independent row-locked transaction. It releases only the exact still-current `VERIFYING` token to `PENDING_UPLOAD`; a committed `READY` row, newer token, different state, or missing row remains untouched.
- Completion finalization failures now return retryable HTTP 503. A rolled-back transaction can be reclaimed immediately by the next complete request, while the valid provider object remains present and outside cleanup.
- Added `MediaReadTransactionService`, which returns a detached immutable READY snapshot from a short read-only transaction. Public attachment downloads resolve that snapshot first and invoke `ObjectStorage.openStream` only after the database transaction has ended.
- Preserved streaming response ownership: the controller still closes the provider stream after transfer, and the detached snapshot contains only the content type, filename, byte size, purpose, media ID, and provider-neutral object location required by the response.
- Updated the media storage operations guide for ambiguous-commit recovery and remote-stream transaction boundaries.

## TDD evidence

- Failure-injection coverage proves a rolled-back finalization invokes the exact-token release, returns 503, permits immediate re-completion, and never calls object deletion or cleanup.
- The uncertain-commit contract proves an already-READY row is never regressed to PENDING by a stale token recovery.
- A Spring transaction-boundary test uses a real transaction interceptor: repository snapshot lookup observes an active transaction, while the mocked provider `openStream` assertion observes no active Spring transaction.

## Verification

- Focused finalization recovery and transaction-boundary suites: passed.
- Backend full suite: `mvn test` — **321 tests, 0 failures, 0 errors, 24 skipped**.
- Backend package: `mvn -DskipTests package` — success.
- Frontend full suite: `npm run test:run` — **176 tests passed**.
- Frontend typecheck: `npm run typecheck` — success.
- Frontend production build: `npm run build` — success.
- Nginx template contract: `npm run test:nginx-template` — success.
- Legacy-route guard: `npm run lint:legacy-routes` — success.
- `git diff --check` — success.

## External gate status

Docker is not installed on this host, so the existing 24 Docker/Testcontainers release gates remain skipped and must run in CI or a Docker-enabled release host. No push was performed. User-owned untracked `.gitignore` and runtime `blog-backend/media/` remain excluded from the commit.
