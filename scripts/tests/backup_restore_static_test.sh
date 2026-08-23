#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

scripts_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

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

printf 'backup/restore static checks passed\n'
