#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

scripts_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
repo_dir=$(CDPATH= cd -- "$scripts_dir/.." && pwd)

bash -n "$scripts_dir/backup.sh"
bash -n "$scripts_dir/restore.sh"

assert_rejected() {
  local script=$1 expected=$2
  shift 2
  local output status=0
  output=$("$script" "$@" 2>&1) || status=$?
  [[ $status -ne 0 ]] || {
    printf 'expected command to fail: %s\n' "$script" >&2
    exit 1
  }
  [[ "$output" == *"$expected"* ]] || {
    printf 'expected output to contain %q, got: %s\n' "$expected" "$output" >&2
    exit 1
  }
}

assert_rejected "$scripts_dir/backup.sh" '--destination is required'
assert_rejected "$scripts_dir/backup.sh" '--project is required' --destination /tmp/backups
assert_rejected "$scripts_dir/restore.sh" '--backup-dir is required'

grep -Fq -- '--single-transaction' "$scripts_dir/backup.sh"
grep -Fq -- '--routines' "$scripts_dir/backup.sh"
grep -Fq -- '--triggers' "$scripts_dir/backup.sh"
grep -Fq 'sha256sum' "$scripts_dir/backup.sh"
grep -Fq 'refusing non-interactive restore' "$scripts_dir/restore.sh"
grep -Fq 'checksum verification failed' "$scripts_dir/restore.sh"
grep -Fq 'create_temp_db "$temp_database"' "$scripts_dir/restore.sh"
grep -Fq 'rollback_db' "$scripts_dir/restore.sh"
grep -Fq 'rollback_media' "$scripts_dir/restore.sh"
grep -Fq -- '--allow-cross-target' "$scripts_dir/restore.sh"
grep -Fq -- '--wait-timeout 120' "$scripts_dir/restore.sh"

# Release media-storage contract: the Compose API is the only container that
# receives R2 credentials. R2 values are deliberately optional at Compose
# interpolation time because Local is the safe default; R2Properties performs
# the required-value check only when BLOG_MEDIA_PROVIDER=r2 is selected.
compose_file="$repo_dir/docker-compose.yml"
production_env="$repo_dir/.env.example"
test_env="$repo_dir/.env.test.example"
grep -Fq 'BLOG_MEDIA_PROVIDER: ${BLOG_MEDIA_PROVIDER:-local}' "$compose_file"
grep -Fq 'BLOG_MEDIA_LOCAL_DIRECTORY: /app/data/media' "$compose_file"
grep -Fq 'media-data:/app/data' "$compose_file"
for variable in BLOG_MEDIA_R2_ACCOUNT_ID BLOG_MEDIA_R2_ACCESS_KEY_ID BLOG_MEDIA_R2_SECRET_ACCESS_KEY BLOG_MEDIA_R2_BUCKET BLOG_MEDIA_R2_ENDPOINT BLOG_MEDIA_R2_PUBLIC_BASE_URL BLOG_MEDIA_R2_LEGACY_BUCKETS BLOG_MEDIA_R2_UPLOAD_URL_TTL; do
  grep -Fq "$variable: \${$variable:-}" "$compose_file"
  grep -q "^$variable=" "$production_env"
done
grep -Fq 'MEDIA_UPLOAD_ORIGIN: ${MEDIA_UPLOAD_ORIGIN:-}' "$compose_file"
grep -Fq 'MEDIA_PUBLIC_ORIGINS: ${MEDIA_PUBLIC_ORIGINS:-}' "$compose_file"
grep -q '^MEDIA_UPLOAD_ORIGIN=' "$production_env"
grep -q '^MEDIA_PUBLIC_ORIGINS=' "$production_env"
grep -Fq 'BLOG_MEDIA_PROVIDER=local' "$production_env"
grep -Fq 'BLOG_MEDIA_PROVIDER=local' "$test_env"
grep -Eq '^BLOG_MEDIA_R2_ACCESS_KEY_ID=$' "$production_env"
grep -Eq '^BLOG_MEDIA_R2_SECRET_ACCESS_KEY=$' "$production_env"
! grep -Fq 'BLOG_MEDIA_R2_' "$test_env"

# Credentials must not be injected into the public Nginx service or baked into
# a frontend build argument. Keep this deliberately structural rather than
# matching line positions in Compose.
web_block=$(sed -n '/^  web:/,/^volumes:/p' "$compose_file")
! grep -Fq 'BLOG_MEDIA_R2_' <<<"$web_block"
! grep -Fq 'BLOG_MEDIA_R2_' "$repo_dir/blog-frontend/Dockerfile"

# R2 always uses S3's required `auto` region internally. Neither base nor
# production configuration may reintroduce a user-configurable region value.
if rg -n 'BLOG_MEDIA_R2_REGION|^\s+region:' "$repo_dir/blog-backend/src/main/resources/application.yml" \
  "$repo_dir/blog-backend/src/main/resources/application-prod.yml"; then
  printf 'R2 region must remain fixed to auto and must not be configurable\n' >&2
  exit 1
fi

printf 'backup/restore static checks passed\n'
