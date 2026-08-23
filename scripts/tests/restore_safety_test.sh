#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'

scripts_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
# shellcheck source=../lib/restore_safety.sh
source "$scripts_dir/lib/restore_safety.sh"

fixture_dir=$(mktemp -d "${TMPDIR:-/tmp}/blog-restore-test.XXXXXX")
trap 'find "$fixture_dir" -mindepth 1 -delete; rmdir "$fixture_dir"' EXIT

mkdir -p "$fixture_dir/safe/images"
printf 'image' >"$fixture_dir/safe/images/avatar.png"
tar -C "$fixture_dir/safe" -czf "$fixture_dir/safe.tar.gz" .
validate_media_archive "$fixture_dir/safe.tar.gz" "$fixture_dir/validated"
[[ -f "$fixture_dir/validated/images/avatar.png" ]]

mkdir -p "$fixture_dir/link-source"
ln -s /etc/passwd "$fixture_dir/link-source/escape"
tar -C "$fixture_dir/link-source" -czf "$fixture_dir/symlink.tar.gz" .
status=0
output=$(validate_media_archive "$fixture_dir/symlink.tar.gz" "$fixture_dir/link-output" 2>&1) || status=$?
[[ $status -ne 0 ]]
[[ "$output" == *'symbolic links are not allowed'* ]]

events="$fixture_dir/events"
forward_fails() { printf 'forward\n' >>"$events"; return 1; }
rollback_succeeds() { printf 'rollback\n' >>"$events"; }
status=0
run_guarded_step forward_fails rollback_succeeds || status=$?
[[ $status -ne 0 ]]
[[ $(cat "$events") == $'forward\nrollback' ]]

printf 'restore safety tests passed\n'
