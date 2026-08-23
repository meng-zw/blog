#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'
umask 077

scripts_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
source "$scripts_dir/lib/backup_manifest.sh"
source "$scripts_dir/lib/restore_safety.sh"

usage() {
  cat <<'EOF'
Usage: scripts/restore.sh --backup-dir DIR --project PROJECT \
  --db-service SERVICE --database DATABASE --media-service SERVICE \
  --media-path ABSOLUTE_PATH [--compose-file FILE] [--allow-cross-target]
EOF
}
die() { printf 'restore: %s\n' "$*" >&2; exit 2; }
require_value() { [[ -n "${2-}" ]] || die "$1 requires a non-empty value"; }

backup_dir='' project='' db_service='' database=''
media_service='' media_path='' compose_file='docker-compose.yml' allow_cross_target=0
while (($#)); do
  case "$1" in
    --backup-dir|--project|--db-service|--database|--media-service|--media-path|--compose-file)
      (($# >= 2)) || die "$1 requires a value"; require_value "$1" "$2"
      case "$1" in
        --backup-dir) backup_dir=$2 ;; --project) project=$2 ;;
        --db-service) db_service=$2 ;; --database) database=$2 ;;
        --media-service) media_service=$2 ;; --media-path) media_path=$2 ;;
        --compose-file) compose_file=$2 ;;
      esac
      shift 2 ;;
    --allow-cross-target) allow_cross_target=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ -n "$backup_dir" ]] || die '--backup-dir is required and must not be empty'
[[ -n "$project" ]] || die '--project is required'
[[ -n "$db_service" ]] || die '--db-service is required'
[[ -n "$database" ]] || die '--database is required'
[[ -n "$media_service" ]] || die '--media-service is required'
[[ -n "$media_path" ]] || die '--media-path is required'
[[ "$project" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] || die 'invalid Compose project name'
[[ "$db_service" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] || die 'invalid database service name'
[[ "$media_service" =~ ^[A-Za-z0-9][A-Za-z0-9_.-]*$ ]] || die 'invalid media service name'
[[ "$database" =~ ^[A-Za-z0-9_]+$ ]] || die 'invalid database name'
[[ "$media_path" == /* ]] || die 'media path must be absolute'
case "$media_path" in /|/app|/app/|.|..|*/../*) die "unsafe media path: $media_path" ;; esac
[[ -f "$compose_file" ]] || die "compose file not found: $compose_file"
[[ -d "$backup_dir" ]] || die "backup directory not found: $backup_dir"
for file in database.sql.gz media.tar.gz manifest.txt SHA256SUMS; do
  [[ -f "$backup_dir/$file" ]] || die "backup is incomplete; missing $file"
done
command -v docker >/dev/null 2>&1 || die 'docker is required'

parse_backup_manifest "$backup_dir/manifest.txt" || die 'invalid backup manifest'
cross_target=0
if ! assert_manifest_target "$project" "$compose_file" "$db_service" "$database" "$media_service" "$media_path"; then
  ((allow_cross_target == 1)) || die 'target mismatch; --allow-cross-target is required'
  cross_target=1
fi

checksum_names=$(awk '{print $2}' "$backup_dir/SHA256SUMS")
[[ $(printf '%s\n' "$checksum_names" | wc -l | tr -d ' ') == 3 ]] || die 'checksum manifest must contain exactly three entries'
for expected in database.sql.gz media.tar.gz manifest.txt; do
  printf '%s\n' "$checksum_names" | grep -Fxq "$expected" || die "checksum manifest is missing $expected"
done
if command -v sha256sum >/dev/null 2>&1; then
  (cd "$backup_dir" && sha256sum --check SHA256SUMS) || die 'checksum verification failed'
elif command -v shasum >/dev/null 2>&1; then
  (cd "$backup_dir" && shasum -a 256 --check SHA256SUMS) || die 'checksum verification failed'
else die 'sha256sum or shasum is required'; fi
gzip -t "$backup_dir/database.sql.gz" || die 'database archive is invalid'

host_validation=$(mktemp -d "${TMPDIR:-/tmp}/blog-media-validation.XXXXXX")
cleanup_host() { find "$host_validation" -mindepth 1 -delete 2>/dev/null || true; rmdir "$host_validation" 2>/dev/null || true; }
trap cleanup_host EXIT
validate_media_archive "$backup_dir/media.tar.gz" "$host_validation/content" || die 'media archive validation failed'

[[ -t 0 && -t 1 ]] || die 'refusing non-interactive restore; run this command in a terminal'
printf '\nRESTORE TARGET (existing data will be replaced)\n'
printf '  Compose project: %s\n  Compose file: %s\n  DB service/database: %s/%s\n' "$project" "$compose_file" "$db_service" "$database"
printf '  Media service/path: %s/%s\n  Backup: %s\n\n' "$media_service" "$media_path" "$backup_dir"
confirmation="RESTORE $project/$database"; printf 'Type exactly "%s": ' "$confirmation"; IFS= read -r answer
[[ "$answer" == "$confirmation" ]] || die 'confirmation did not match; nothing was changed'
if ((cross_target == 1)); then
  cross_confirmation="CROSS TARGET $project/$database"
  printf 'Manifest target is %s/%s. Type exactly "%s": ' "$MANIFEST_COMPOSE_PROJECT" "$MANIFEST_DATABASE_NAME" "$cross_confirmation"
  IFS= read -r answer
  [[ "$answer" == "$cross_confirmation" ]] || die 'cross-target confirmation did not match; nothing was changed'
fi

run_id="$(date -u '+%Y%m%dT%H%M%SZ').$$"
audit_file="$backup_dir/restore-audit-$run_id.log" recovery_dir="$backup_dir/recovery-$run_id"
mkdir -p -- "$recovery_dir"
cat >"$audit_file" <<EOF
started_at_utc=$(date -u '+%Y-%m-%dT%H:%M:%SZ')
backup=$backup_dir
manifest_project=$MANIFEST_COMPOSE_PROJECT
manifest_database=$MANIFEST_DATABASE_NAME
target_project=$project
target_database=$database
target_db_service=$db_service
target_media_service=$media_service
target_media_path=$media_path
cross_target=$cross_target
status=started
EOF

compose=(docker compose --file "$compose_file" --project-name "$project")
temp_database="${database:0:30}_restore_$$_${RANDOM}"
media_parent=${media_path%/*}; media_name=${media_path##*/}
media_temp="$media_parent/.${media_name}.restore-$run_id"
media_old="$media_parent/.${media_name}.previous-$run_id"
api_stopped=0 target_changed=0 media_swapped=0 success=0

root_mysql() {
  "${compose[@]}" exec -T "$db_service" sh -eu -c ': "${MYSQL_ROOT_PASSWORD:?}"; exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" --batch --skip-column-names'
}
drop_create_db() { printf 'DROP DATABASE IF EXISTS `%s`; CREATE DATABASE `%s` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n' "$1" "$1" | root_mysql; }
create_temp_db() { printf 'CREATE DATABASE `%s` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;\n' "$1" | root_mysql; }
import_db() {
  gzip -dc "$1" | "${compose[@]}" exec -T "$db_service" sh -eu -c ': "${MYSQL_ROOT_PASSWORD:?}"; exec mysql -uroot -p"$MYSQL_ROOT_PASSWORD" "$1"' sh "$2"
}
validate_db() {
  local count
  count=$(printf 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema="%s";\n' "$1" | root_mysql)
  [[ "$count" =~ ^[0-9]+$ && "$count" -gt 0 ]] || return 1
  printf 'SELECT 1 FROM `%s`.`admin_account` LIMIT 1;\n' "$1" | root_mysql >/dev/null
}
dump_db() {
  "${compose[@]}" exec -T "$db_service" sh -eu -c ': "${MYSQL_ROOT_PASSWORD:?}"; exec mysqldump --single-transaction --routines --triggers --add-drop-table --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" "$1"' sh "$1" | gzip -9 >"$2"
}
media_run() { "${compose[@]}" run --rm --no-deps -T --entrypoint sh "$media_service" "$@"; }
rollback_db() {
  [[ -s "$recovery_dir/database-before-restore.sql.gz" ]] || return 1
  printf 'restore: rolling back database\n' >&2
  drop_create_db "$database" && import_db "$recovery_dir/database-before-restore.sql.gz" "$database" && validate_db "$database"
}
rollback_media() {
  media_run -eu -c 'target=$1; old=$2; failed=$3; [ -d "$old" ] || exit 1; [ ! -e "$target" ] || mv "$target" "$failed"; mv "$old" "$target"; [ ! -e "$failed" ] || { find "$failed" -mindepth 1 -delete; rmdir "$failed"; }' sh "$media_path" "$media_old" "$media_temp.failed"
}
on_exit() {
  local status=$?; trap - EXIT INT TERM; set +e; cleanup_host
  if ((status != 0 || success == 0)); then
    # A failed health wait may leave the write service running; stop it again
    # before touching either recovery target.
    "${compose[@]}" stop "$media_service" >/dev/null 2>&1 || true
    ((media_swapped == 0)) || rollback_media
    ((target_changed == 0)) || rollback_db
    printf 'finished_at_utc=%s\nstatus=failed\nrecovery_dir=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$recovery_dir" >>"$audit_file"
  fi
  printf 'DROP DATABASE IF EXISTS `%s`;\n' "$temp_database" | root_mysql >/dev/null 2>&1 || true
  media_run -eu -c '[ ! -e "$1" ] || { find "$1" -mindepth 1 -delete; rmdir "$1"; }' sh "$media_temp" >/dev/null 2>&1 || true
  if ((api_stopped == 1)); then "${compose[@]}" up -d --no-deps "$media_service" >/dev/null 2>&1 || true; fi
  exit "$status"
}
trap on_exit EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

# Validate in a unique temporary database before touching live data.
create_temp_db "$temp_database"
import_db "$backup_dir/database.sql.gz" "$temp_database"
validate_db "$temp_database" || die 'temporary database validation failed'

# Extract to a sibling directory on the same mounted filesystem.
media_run -eu -c 'target=$1; staged=$2; parent=${target%/*}; [ -d "$parent" ] && [ ! -e "$staged" ]; mkdir "$staged"; tar -C "$staged" -xzf -' sh "$media_path" "$media_temp" <"$backup_dir/media.tar.gz"

"${compose[@]}" stop "$media_service"; api_stopped=1
dump_db "$database" "$recovery_dir/database-before-restore.sql.gz"
gzip -t "$recovery_dir/database-before-restore.sql.gz"

target_changed=1
drop_create_db "$database"
import_db "$backup_dir/database.sql.gz" "$database"
validate_db "$database" || die 'restored target database validation failed'

media_run -eu -c 'target=$1; staged=$2; old=$3; [ -d "$target" ] && [ -d "$staged" ] && [ ! -e "$old" ]; mv "$target" "$old"; if ! mv "$staged" "$target"; then mv "$old" "$target"; exit 1; fi' sh "$media_path" "$media_temp" "$media_old"
media_swapped=1
"${compose[@]}" up -d --no-deps --wait --wait-timeout 120 "$media_service"; api_stopped=0
success=1; media_swapped=0
printf 'finished_at_utc=%s\nstatus=complete\nrecovery_dir=%s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$recovery_dir" >>"$audit_file"
printf 'Restore complete. Audit: %s; database recovery: %s; media recovery: %s\n' \
  "$audit_file" "$recovery_dir/database-before-restore.sql.gz" "$media_old"
