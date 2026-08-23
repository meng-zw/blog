#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

scripts_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
# shellcheck source=../lib/backup_manifest.sh
source "$scripts_dir/lib/backup_manifest.sh"

fixture_dir=$(mktemp -d "${TMPDIR:-/tmp}/blog-manifest-test.XXXXXX")
trap 'find "$fixture_dir" -mindepth 1 -delete; rmdir "$fixture_dir"' EXIT

write_valid_manifest() {
  cat >"$fixture_dir/manifest.txt" <<'EOF'
created_at_utc=20260823T120000Z
compose_project=xiaom-blog
compose_file=docker-compose.yml
database_service=db
database_name=blog
media_service=api
media_path=/app/data/media
EOF
}

assert_parse_fails() {
  local expected=$1 output status=0
  output=$(parse_backup_manifest "$fixture_dir/manifest.txt" 2>&1) || status=$?
  [[ $status -ne 0 ]] || {
    printf 'expected manifest parse failure\n' >&2
    exit 1
  }
  [[ "$output" == *"$expected"* ]] || {
    printf 'expected %q in parser output, got: %s\n' "$expected" "$output" >&2
    exit 1
  }
}

write_valid_manifest
parse_backup_manifest "$fixture_dir/manifest.txt"
[[ "$MANIFEST_COMPOSE_PROJECT" == 'xiaom-blog' ]]
[[ "$MANIFEST_DATABASE_SERVICE" == 'db' ]]
[[ "$MANIFEST_DATABASE_NAME" == 'blog' ]]
[[ "$MANIFEST_MEDIA_SERVICE" == 'api' ]]
[[ "$MANIFEST_MEDIA_PATH" == '/app/data/media' ]]

write_valid_manifest
printf 'unknown_key=value\n' >>"$fixture_dir/manifest.txt"
assert_parse_fails 'unknown manifest key'

write_valid_manifest
printf 'database_name=other\n' >>"$fixture_dir/manifest.txt"
assert_parse_fails 'duplicate manifest key'

write_valid_manifest
sed '/^media_path=/d' "$fixture_dir/manifest.txt" >"$fixture_dir/incomplete"
mv "$fixture_dir/incomplete" "$fixture_dir/manifest.txt"
assert_parse_fails 'missing manifest key: media_path'

write_valid_manifest
printf '\001' >>"$fixture_dir/manifest.txt"
assert_parse_fails 'control character'

write_valid_manifest
assert_manifest_target \
  'xiaom-blog' 'docker-compose.yml' 'db' 'blog' 'api' '/app/data/media'

status=0
output=$(assert_manifest_target \
  'wrong-project' 'docker-compose.yml' 'db' 'blog' 'api' '/app/data/media' 2>&1) || status=$?
[[ $status -ne 0 ]]
[[ "$output" == *'does not match backup manifest'* ]]

printf 'manifest contract tests passed\n'
