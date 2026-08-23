#!/usr/bin/env bash
set -Eeuo pipefail
IFS=$'\n\t'
umask 077

usage() {
  cat <<'EOF'
Usage: scripts/backup.sh \
  --destination DIR \
  --project COMPOSE_PROJECT \
  --db-service SERVICE \
  --database DATABASE \
  --media-service SERVICE \
  --media-path CONTAINER_PATH \
  [--compose-file FILE]

Creates DIR/YYYYmmddTHHMMSSZ/ containing database.sql.gz,
media.tar.gz, manifest.txt and SHA256SUMS.
EOF
}

die() {
  printf 'backup: %s\n' "$*" >&2
  exit 2
}

require_value() {
  local option=$1 value=${2-}
  [[ -n "$value" ]] || die "$option requires a non-empty value"
}

destination=''
project=''
db_service=''
database=''
media_service=''
media_path=''
compose_file='docker-compose.yml'

while (($#)); do
  case "$1" in
    --destination|--project|--db-service|--database|--media-service|--media-path|--compose-file)
      (($# >= 2)) || die "$1 requires a value"
      require_value "$1" "$2"
      case "$1" in
        --destination) destination=$2 ;;
        --project) project=$2 ;;
        --db-service) db_service=$2 ;;
        --database) database=$2 ;;
        --media-service) media_service=$2 ;;
        --media-path) media_path=$2 ;;
        --compose-file) compose_file=$2 ;;
      esac
      shift 2
      ;;
    -h|--help) usage; exit 0 ;;
    *) die "unknown argument: $1" ;;
  esac
done

[[ -n "$destination" ]] || die '--destination is required and must not be empty'
[[ -n "$project" ]] || die '--project is required'
[[ -n "$db_service" ]] || die '--db-service is required'
[[ -n "$database" ]] || die '--database is required'
[[ -n "$media_service" ]] || die '--media-service is required'
[[ -n "$media_path" ]] || die '--media-path is required'
case "$media_path" in
  /|/app|/app/|.|..) die "unsafe media path: $media_path" ;;
esac
[[ -f "$compose_file" ]] || die "compose file not found: $compose_file"
command -v docker >/dev/null 2>&1 || die 'docker is required'
command -v gzip >/dev/null 2>&1 || die 'gzip is required'

case "$destination" in
  /|.|..) die "unsafe destination: $destination" ;;
esac

timestamp=$(date -u '+%Y%m%dT%H%M%SZ')
backup_dir=${destination%/}/$timestamp
[[ ! -e "$backup_dir" ]] || die "backup directory already exists: $backup_dir"
mkdir -p -- "$backup_dir"

cleanup_on_error() {
  local status=$?
  if ((status != 0)); then
    printf 'backup: failed; removing incomplete backup %s\n' "$backup_dir" >&2
    find "$backup_dir" -mindepth 1 -delete 2>/dev/null || true
    rmdir "$backup_dir" 2>/dev/null || true
  fi
  exit "$status"
}
trap cleanup_on_error EXIT

compose=(docker compose --file "$compose_file" --project-name "$project")

printf 'Backing up Compose project %s, database %s in service %s\n' \
  "$project" "$database" "$db_service"

"${compose[@]}" exec -T "$db_service" sh -eu -c '
  : "${MYSQL_USER:?MYSQL_USER is not set in the database service}"
  : "${MYSQL_PASSWORD:?MYSQL_PASSWORD is not set in the database service}"
  exec mysqldump \
    --single-transaction \
    --routines \
    --triggers \
    --add-drop-table \
    --default-character-set=utf8mb4 \
    --user="$MYSQL_USER" \
    --password="$MYSQL_PASSWORD" \
    "$1"
' sh "$database" | gzip -9 >"$backup_dir/database.sql.gz"

"${compose[@]}" exec -T "$media_service" sh -eu -c '
  media_path=$1
  [ -d "$media_path" ] || {
    printf "media directory not found: %s\n" "$media_path" >&2
    exit 1
  }
  exec tar -C "$media_path" -czf - .
' sh "$media_path" >"$backup_dir/media.tar.gz"

cat >"$backup_dir/manifest.txt" <<EOF
created_at_utc=$timestamp
compose_project=$project
compose_file=$compose_file
database_service=$db_service
database_name=$database
media_service=$media_service
media_path=$media_path
EOF

if command -v sha256sum >/dev/null 2>&1; then
  (cd "$backup_dir" && sha256sum database.sql.gz media.tar.gz manifest.txt >SHA256SUMS)
elif command -v shasum >/dev/null 2>&1; then
  (cd "$backup_dir" && shasum -a 256 database.sql.gz media.tar.gz manifest.txt >SHA256SUMS)
else
  die 'sha256sum or shasum is required'
fi

trap - EXIT
printf 'Backup complete: %s\n' "$backup_dir"
