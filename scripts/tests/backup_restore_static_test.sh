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

# Normalizes a YAML/.env assignment value. YAML comments preceded by whitespace
# are removed before quote handling; quotes are removed only when they wrap the
# complete value, so `${SECRET}suffix` cannot be mistaken for an indirection
# merely because it starts with a variable reference.
normalize_cloudreve_value() {
  local value=$1 first last
  value="${value%%[[:space:]]#*}"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  if [[ ${#value} -ge 2 ]]; then
    first=${value:0:1}
    last=${value: -1}
    if [[ ( "$first" == '"' && "$last" == '"' ) || ( "$first" == "'" && "$last" == "'" ) ]]; then
      value=${value:1:${#value}-2}
    fi
  fi
  printf '%s' "$value"
}

# An assigned credential must be absent, an entire Compose/Spring variable
# reference, or an entire documentation placeholder. Prefix matching is
# deliberately unsafe because it permits `${SECRET}leaked-value`.
is_allowed_cloudreve_credential_value() {
  local value variable_reference_pattern placeholder_pattern
  value=$(normalize_cloudreve_value "$1")
  variable_reference_pattern='^\$\{[^}]+\}$'
  placeholder_pattern='^<[^<>]+>$'
  [[ -z "$value" || "$value" =~ $variable_reference_pattern || "$value" =~ $placeholder_pattern ]]
}

is_private_cloudreve_endpoint() {
  local value
  value=$(normalize_cloudreve_value "$1")
  printf '%s\n' "$value" | rg --pcre2 -q '^https?://(10\.(?:[0-9]{1,3}\.){2}[0-9]{1,3}|192\.168\.(?:[0-9]{1,3}\.)[0-9]{1,3}|172\.(?:1[6-9]|2[0-9]|3[0-1])\.(?:[0-9]{1,3}\.)[0-9]{1,3})(?::[0-9]{1,5})?(?:[/?#]|$)'
}

# Returns success and prints a finding when a tracked Cloudreve deployment or
# configuration source contains a concrete credential or private-IP endpoint.
# It parses each assignment value and only allows whole-value `${...}` or
# `<...>` placeholders; Compose/Spring indirection may not have a suffix.
scan_cloudreve_configuration() {
  local assignment_pattern assignment_content_pattern findings file line content key canonical_key value nocasematch_was_enabled=0
  assignment_pattern='(?i)^[[:space:]]*(BLOG_MEDIA_CLOUDREVE_(BASE_URL|AUTHORIZATION_URI|TOKEN_URI|REFRESH_URI|USERINFO_URI|REDIRECT_URI|CLIENT_ID|CLIENT_SECRET|POLICY_ID)|BLOG_MEDIA_TOKEN_ENCRYPTION_KEY|base-url|authorization-uri|token-uri|refresh-uri|userinfo-uri|redirect-uri|client-id|client-secret|policy-id|token-encryption-key)[[:space:]]*[:=]'
  assignment_content_pattern='^[[:space:]]*(BLOG_MEDIA_CLOUDREVE_(BASE_URL|AUTHORIZATION_URI|TOKEN_URI|REFRESH_URI|USERINFO_URI|REDIRECT_URI|CLIENT_ID|CLIENT_SECRET|POLICY_ID)|BLOG_MEDIA_TOKEN_ENCRYPTION_KEY|base-url|authorization-uri|token-uri|refresh-uri|userinfo-uri|redirect-uri|client-id|client-secret|policy-id|token-encryption-key)[[:space:]]*[:=][[:space:]]*(.*)$'
  if shopt -q nocasematch; then
    nocasematch_was_enabled=1
  else
    shopt -s nocasematch
  fi
  findings=''
  while IFS=: read -r file line content; do
    [[ "$content" =~ $assignment_content_pattern ]] || continue
    key=${BASH_REMATCH[1]}
    canonical_key=$(printf '%s' "$key" | tr '[:upper:]' '[:lower:]')
    value=${BASH_REMATCH[3]}
    case "$canonical_key" in
      blog_media_cloudreve_client_id|blog_media_cloudreve_client_secret|blog_media_cloudreve_policy_id|blog_media_token_encryption_key|client-id|client-secret|policy-id|token-encryption-key)
        if ! is_allowed_cloudreve_credential_value "$value"; then
          findings+="${findings:+$'\n'}$file:$line:$content"
        fi
        ;;
      *)
        if is_private_cloudreve_endpoint "$value"; then
          findings+="${findings:+$'\n'}$file:$line:$content"
        fi
        ;;
    esac
  done < <(rg --pcre2 -n --with-filename --no-heading -- "$assignment_pattern" "$@" 2>/dev/null || true)
  if (( ! nocasematch_was_enabled )); then
    shopt -u nocasematch
  fi
  [[ -n "$findings" ]] || return 1
  printf '%s\n' "$findings"
  return 0
}

assert_cloudreve_scan_rejects() {
  local fixture=$1 expected=$2 output
  if output=$(scan_cloudreve_configuration "$fixture"); then
    [[ "$output" == *"$expected"* ]] || {
      printf 'expected Cloudreve scan finding %q, got: %s\n' "$expected" "$output" >&2
      exit 1
    }
  else
    printf 'expected Cloudreve scan to reject fixture: %s\n' "$fixture" >&2
    exit 1
  fi
}

assert_cloudreve_scan_allows() {
  local fixture=$1 output
  if output=$(scan_cloudreve_configuration "$fixture"); then
    printf 'expected Cloudreve scan to allow fixture %s, got: %s\n' "$fixture" "$output" >&2
    exit 1
  fi
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

# Cloudreve uses the same API-only secret boundary as R2. Its default remains
# opt-in so an existing Local deployment does not need an OAuth app or secrets.
grep -Fq 'BLOG_MEDIA_CLOUDREVE_ENABLED: ${BLOG_MEDIA_CLOUDREVE_ENABLED:-false}' "$compose_file"
grep -Fq 'BLOG_MEDIA_PROVIDER=local' "$production_env"
grep -Eq '^BLOG_MEDIA_CLOUDREVE_ENABLED=false$' "$production_env"
for variable in \
  BLOG_MEDIA_CLOUDREVE_BASE_URL \
  BLOG_MEDIA_CLOUDREVE_AUTHORIZATION_URI \
  BLOG_MEDIA_CLOUDREVE_TOKEN_URI \
  BLOG_MEDIA_CLOUDREVE_REFRESH_URI \
  BLOG_MEDIA_CLOUDREVE_USERINFO_URI \
  BLOG_MEDIA_CLOUDREVE_REDIRECT_URI \
  BLOG_MEDIA_CLOUDREVE_CLIENT_ID \
  BLOG_MEDIA_CLOUDREVE_CLIENT_SECRET \
  BLOG_MEDIA_CLOUDREVE_POLICY_ID \
  BLOG_MEDIA_TOKEN_ENCRYPTION_KEY; do
  grep -Fq "$variable: \${$variable:-}" "$compose_file"
  grep -q "^$variable=" "$production_env"
done
grep -Eq '^BLOG_MEDIA_CLOUDREVE_CLIENT_ID=$' "$production_env"
grep -Eq '^BLOG_MEDIA_CLOUDREVE_CLIENT_SECRET=$' "$production_env"
grep -Eq '^BLOG_MEDIA_TOKEN_ENCRYPTION_KEY=$' "$production_env"
grep -Eq '^BLOG_MEDIA_CLOUDREVE_ALLOW_TRUSTED_INTERNAL_HTTP=false$' "$production_env"
grep -Fq 'BLOG_MEDIA_CLOUDREVE_ALLOW_TRUSTED_INTERNAL_HTTP: ${BLOG_MEDIA_CLOUDREVE_ALLOW_TRUSTED_INTERNAL_HTTP:-false}' "$compose_file"
grep -Fq 'BLOG_MEDIA_CLOUDREVE_API_BASE_PATH: ${BLOG_MEDIA_CLOUDREVE_API_BASE_PATH:-/api/v4}' "$compose_file"
grep -Fq 'BLOG_MEDIA_CLOUDREVE_UPLOAD_CALLBACK_BASE_PATH: ${BLOG_MEDIA_CLOUDREVE_UPLOAD_CALLBACK_BASE_PATH:-/api/v4/callback}' "$compose_file"
grep -Fq 'BLOG_MEDIA_CLOUDREVE_ROOT_PATH: ${BLOG_MEDIA_CLOUDREVE_ROOT_PATH:-/blog}' "$compose_file"
grep -Fq 'BLOG_MEDIA_CLOUDREVE_CONNECT_TIMEOUT: ${BLOG_MEDIA_CLOUDREVE_CONNECT_TIMEOUT:-5s}' "$compose_file"
grep -Fq 'BLOG_MEDIA_CLOUDREVE_REQUEST_TIMEOUT: ${BLOG_MEDIA_CLOUDREVE_REQUEST_TIMEOUT:-30s}' "$compose_file"
grep -Eq '^BLOG_MEDIA_CLOUDREVE_API_BASE_PATH=/api/v4$' "$production_env"
grep -Eq '^BLOG_MEDIA_CLOUDREVE_UPLOAD_CALLBACK_BASE_PATH=/api/v4/callback$' "$production_env"
grep -Eq '^BLOG_MEDIA_CLOUDREVE_ROOT_PATH=/blog$' "$production_env"
grep -Eq '^BLOG_MEDIA_CLOUDREVE_CONNECT_TIMEOUT=5s$' "$production_env"
grep -Eq '^BLOG_MEDIA_CLOUDREVE_REQUEST_TIMEOUT=30s$' "$production_env"

# Compose must retain API-side callback and endpoint overrides exactly as
# environment interpolation, rather than baking a Cloudreve host or callback
# into a container image. The public web service must never see Cloudreve data.
! grep -Fq 'BLOG_MEDIA_CLOUDREVE_' <<<"$web_block"
! grep -Fq 'BLOG_MEDIA_TOKEN_ENCRYPTION_KEY' <<<"$web_block"
! grep -Fq 'BLOG_MEDIA_CLOUDREVE_' "$repo_dir/blog-frontend/Dockerfile"
! grep -Fq 'BLOG_MEDIA_TOKEN_ENCRYPTION_KEY' "$repo_dir/blog-frontend/Dockerfile"
! grep -Fq 'BLOG_MEDIA_CLOUDREVE_' "$repo_dir/blog-frontend/nginx/default.conf.template"
! grep -Fq 'BLOG_MEDIA_TOKEN_ENCRYPTION_KEY' "$repo_dir/blog-frontend/nginx/default.conf.template"
grep -Fq 'BLOG_MEDIA_CLOUDREVE_REDIRECT_URI: ${BLOG_MEDIA_CLOUDREVE_REDIRECT_URI:-}' "$compose_file"
grep -Fq 'BLOG_MEDIA_CLOUDREVE_AUTHORIZATION_URI: ${BLOG_MEDIA_CLOUDREVE_AUTHORIZATION_URI:-}' "$compose_file"
grep -Fq 'BLOG_MEDIA_CLOUDREVE_TOKEN_URI: ${BLOG_MEDIA_CLOUDREVE_TOKEN_URI:-}' "$compose_file"
grep -Fq 'BLOG_MEDIA_CLOUDREVE_REFRESH_URI: ${BLOG_MEDIA_CLOUDREVE_REFRESH_URI:-}' "$compose_file"
grep -Fq 'BLOG_MEDIA_CLOUDREVE_USERINFO_URI: ${BLOG_MEDIA_CLOUDREVE_USERINFO_URI:-}' "$compose_file"
grep -Fq 'BLOG_MEDIA_CLOUDREVE_API_BASE_PATH: ${BLOG_MEDIA_CLOUDREVE_API_BASE_PATH:-/api/v4}' "$compose_file"
grep -Fq 'BLOG_MEDIA_CLOUDREVE_UPLOAD_CALLBACK_BASE_PATH: ${BLOG_MEDIA_CLOUDREVE_UPLOAD_CALLBACK_BASE_PATH:-/api/v4/callback}' "$compose_file"
grep -Fq 'Files.Read' "$repo_dir/blog-backend/src/main/java/com/blog/media/storage/cloudreve/CloudreveOAuthClient.java"
grep -Fq 'Files.Read' "$repo_dir/docs/cloudreve-media.md"

# Example configuration is intentionally credential-free and must not grow a
# concrete Cloudreve endpoint (including a private instance address).
! rg -n '^BLOG_MEDIA_CLOUDREVE_(BASE_URL|AUTHORIZATION_URI|TOKEN_URI|REFRESH_URI|USERINFO_URI|REDIRECT_URI|CLIENT_ID|CLIENT_SECRET|POLICY_ID)=.+$' "$production_env"
! rg -n '^BLOG_MEDIA_TOKEN_ENCRYPTION_KEY=.+$' "$production_env"
# Scan all tracked deployment/configuration sources: Compose, both environment
# examples, Spring YAML, README/docs, and Cloudreve source. Scope the IP check
# to Cloudreve configuration keys so unrelated Compose loopback health checks
# remain allowed. This intentionally detects both `.env` (`=`) and YAML (`:`)
# supplied Client IDs/secrets and encryption keys.
cloudreve_sources=()
while IFS= read -r source_file; do
  cloudreve_sources+=("$repo_dir/$source_file")
done < <(git -C "$repo_dir" ls-files -- .env.example .env.test.example docker-compose.yml README.md docs \
  blog-backend/src/main/resources blog-backend/src/main/java/com/blog/media/storage/cloudreve)
if findings=$(scan_cloudreve_configuration "${cloudreve_sources[@]}"); then
  printf 'concrete Cloudreve deployment value found:\n%s\n' "$findings" >&2
  exit 1
fi

# Mutation fixtures prove Compose-style YAML secrets and private Cloudreve
# endpoints are caught, rather than only checking `.env` assignment syntax.
fixture_dir=$(mktemp -d "${TMPDIR:-/tmp}/cloudreve-static-contract.XXXXXX")
trap 'rm -rf "$fixture_dir"' EXIT
secret_fixture="$fixture_dir/compose-secret.yml"
ip_fixture="$fixture_dir/compose-private-ip.yml"
quoted_ip_fixture="$fixture_dir/compose-quoted-private-ip.yml"
commented_ip_fixture="$fixture_dir/compose-commented-private-ip.yml"
quoted_commented_ip_fixture="$fixture_dir/compose-quoted-commented-private-ip.yml"
appended_secret_fixture="$fixture_dir/compose-appended-secret.yml"
quoted_placeholder_fixture="$fixture_dir/compose-quoted-placeholder.yml"
placeholder_fixture="$fixture_dir/compose-placeholder.yml"
appended_placeholder_fixture="$fixture_dir/compose-appended-placeholder.yml"
mixed_case_secret_fixture="$fixture_dir/compose-mixed-case-secret.yml"
mixed_case_ip_fixture="$fixture_dir/compose-mixed-case-private-ip.yml"
mixed_case_placeholder_fixture="$fixture_dir/compose-mixed-case-placeholder.yml"
printf '%s\n' 'BLOG_MEDIA_CLOUDREVE_CLIENT_SECRET: supplied-client-secret' > "$secret_fixture"
printf '%s\n' 'BLOG_MEDIA_CLOUDREVE_BASE_URL: http://192.168.10.20:5212' > "$ip_fixture"
printf '%s\n' 'BLOG_MEDIA_CLOUDREVE_BASE_URL: "http://192.168.10.20:5212"' > "$quoted_ip_fixture"
printf '%s\n' 'BLOG_MEDIA_CLOUDREVE_BASE_URL: http://192.168.10.20:5212 # internal' > "$commented_ip_fixture"
printf '%s\n' 'BLOG_MEDIA_CLOUDREVE_BASE_URL: "http://192.168.10.20:5212" # internal' > "$quoted_commented_ip_fixture"
printf '%s\n' 'BLOG_MEDIA_CLOUDREVE_CLIENT_SECRET: ${SECRET}leaked-value' > "$appended_secret_fixture"
printf '%s\n' 'BLOG_MEDIA_CLOUDREVE_CLIENT_SECRET: "${SECRET}"' > "$quoted_placeholder_fixture"
printf '%s\n' 'BLOG_MEDIA_CLOUDREVE_CLIENT_SECRET: <PLACEHOLDER>' > "$placeholder_fixture"
printf '%s\n' 'BLOG_MEDIA_CLOUDREVE_CLIENT_SECRET: <PLACEHOLDER>suffix' > "$appended_placeholder_fixture"
printf '%s\n' 'Client-Secret: supplied-client-secret' > "$mixed_case_secret_fixture"
printf '%s\n' 'BLOG_media_cloudreve_base_url: "http://192.168.10.20:5212"' > "$mixed_case_ip_fixture"
printf '%s\n' 'Client-Secret: "${SECRET}"' > "$mixed_case_placeholder_fixture"
assert_cloudreve_scan_rejects "$secret_fixture" 'BLOG_MEDIA_CLOUDREVE_CLIENT_SECRET'
assert_cloudreve_scan_rejects "$ip_fixture" 'BLOG_MEDIA_CLOUDREVE_BASE_URL'
assert_cloudreve_scan_rejects "$quoted_ip_fixture" 'BLOG_MEDIA_CLOUDREVE_BASE_URL'
assert_cloudreve_scan_rejects "$commented_ip_fixture" 'BLOG_MEDIA_CLOUDREVE_BASE_URL'
assert_cloudreve_scan_rejects "$quoted_commented_ip_fixture" 'BLOG_MEDIA_CLOUDREVE_BASE_URL'
assert_cloudreve_scan_rejects "$appended_secret_fixture" 'BLOG_MEDIA_CLOUDREVE_CLIENT_SECRET'
assert_cloudreve_scan_allows "$quoted_placeholder_fixture"
assert_cloudreve_scan_allows "$placeholder_fixture"
assert_cloudreve_scan_rejects "$appended_placeholder_fixture" 'BLOG_MEDIA_CLOUDREVE_CLIENT_SECRET'
assert_cloudreve_scan_rejects "$mixed_case_secret_fixture" 'Client-Secret'
assert_cloudreve_scan_rejects "$mixed_case_ip_fixture" 'BLOG_media_cloudreve_base_url'
assert_cloudreve_scan_allows "$mixed_case_placeholder_fixture"
grep -Fq 'Cloudreve/storage-policy backup' "$repo_dir/docs/cloudreve-media.md"
grep -Fq 'retention/PITR' "$repo_dir/docs/cloudreve-media.md"
grep -Fq 'post-restore reconciliation' "$repo_dir/docs/cloudreve-media.md"

# R2 always uses S3's required `auto` region internally. Neither base nor
# production configuration may reintroduce a user-configurable region value.
if rg -n 'BLOG_MEDIA_R2_REGION|^\s+region:' "$repo_dir/blog-backend/src/main/resources/application.yml" \
  "$repo_dir/blog-backend/src/main/resources/application-prod.yml"; then
  printf 'R2 region must remain fixed to auto and must not be configurable\n' >&2
  exit 1
fi

printf 'backup/restore static checks passed\n'
