# Final whole-branch fix round 3 report

## Implemented

- Added durable `UPLOADING` and `VERIFYING` claims backed by a Flyway V12 `operation_token` column. Both upload completion and Local proxy writes now acquire a pessimistic row lock in a short committed transaction, perform storage I/O without a database lock, and finalize through another token-checked transaction.
- Serialized completion with background cleanup. Cleanup re-locks the row and cannot claim a current `VERIFYING`/`UPLOADING` operation; if cleanup wins first, the later operation claim is rejected. Expired claims become `ABANDONED`, have their token cleared, and enter the same idempotent cleanup path.
- Reworked authoritative validation failures into a durable workflow: persist `FAILED` first, delete the object outside that transaction, then independently finalize `DELETED`. A provider failure or database failure after object deletion leaves a retryable database state rather than `PENDING_UPLOAD` with a missing object.
- Kept transient HEAD/GET/open/read failures retryable by releasing a valid `VERIFYING` claim back to `PENDING_UPLOAD`. Storage objects are never deleted for these failures.
- Added structured warning logs for cleanup failures with `mediaId`, provider, and error category. Provider exception details remain available only in server logs; public object-storage 404/503 responses now use generic localized messages and cannot expose object keys.
- Updated the media storage runbook for the transaction boundaries, claims, crash recovery, and cleanup behavior.

## TDD and concurrency evidence

- Unit contracts cover row-locked claim/finalization, stale-token rejection, active-claim cleanup exclusion, expired-claim recovery, proxy claim release, transient completion retry, terminal-state-before-delete ordering, failed terminal persistence, database finalization failure, cleanup retry, and sanitized errors.
- `MediaOperationConcurrencyMySqlIntegrationTest` races the real Spring transactional verification and cleanup services against the same MySQL row 12 times. It permits only one of two outcomes: `VERIFYING` with matching token and no cleanup claim, or `ABANDONED` with cleanup authority and a rejected verifier. Docker-unavailable hosts skip this explicit external gate.

## Verification

- Focused media/error suites: passed.
- Backend full suite: `mvn test` — **317 tests, 0 failures, 0 errors, 24 skipped**.
- Backend package: `mvn -DskipTests package` — success.
- Frontend full suite: `npm run test:run` — **176 tests passed**.
- Frontend typecheck: `npm run typecheck` — success.
- Frontend production build: `npm run build` — success.
- Nginx template contract: `npm run test:nginx-template` — success.
- Legacy-route guard: `npm run lint:legacy-routes` — success.
- `git diff --check` — success.

## External gate status

Docker is not installed on this host (`docker: command not found`). Consequently the 24 Docker/Testcontainers-backed tests, including the new MySQL concurrency test, were reported as skipped, and `docker compose config` could not execute. They remain mandatory CI or Docker-enabled release gates. No push was performed, and user-owned untracked `.gitignore` plus runtime `blog-backend/media/` remain excluded from the commit.
