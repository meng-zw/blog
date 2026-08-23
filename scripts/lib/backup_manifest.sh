#!/usr/bin/env bash

manifest_die() {
  printf 'manifest: %s\n' "$*" >&2
  return 1
}

parse_backup_manifest() {
  local manifest_file=$1 line key value
  local seen='|'
  local expected_keys=(
    created_at_utc compose_project compose_file database_service
    database_name media_service media_path
  )

  [[ -f "$manifest_file" ]] || manifest_die "file not found: $manifest_file" || return
  if LC_ALL=C grep -q '[[:cntrl:]]' "$manifest_file"; then
    manifest_die 'control character found in manifest' || return
  fi

  MANIFEST_CREATED_AT_UTC=''
  MANIFEST_COMPOSE_PROJECT=''
  MANIFEST_COMPOSE_FILE=''
  MANIFEST_DATABASE_SERVICE=''
  MANIFEST_DATABASE_NAME=''
  MANIFEST_MEDIA_SERVICE=''
  MANIFEST_MEDIA_PATH=''

  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" == *=* ]] || manifest_die 'malformed manifest line' || return
    key=${line%%=*}
    value=${line#*=}
    [[ -n "$value" ]] || manifest_die "empty manifest value: $key" || return
    case "$key" in
      created_at_utc|compose_project|compose_file|database_service|database_name|media_service|media_path) ;;
      *) manifest_die "unknown manifest key: $key" || return ;;
    esac
    [[ "$seen" != *"|$key|"* ]] || manifest_die "duplicate manifest key: $key" || return
    seen="${seen}${key}|"
    case "$key" in
      created_at_utc) MANIFEST_CREATED_AT_UTC=$value ;;
      compose_project) MANIFEST_COMPOSE_PROJECT=$value ;;
      compose_file) MANIFEST_COMPOSE_FILE=$value ;;
      database_service) MANIFEST_DATABASE_SERVICE=$value ;;
      database_name) MANIFEST_DATABASE_NAME=$value ;;
      media_service) MANIFEST_MEDIA_SERVICE=$value ;;
      media_path) MANIFEST_MEDIA_PATH=$value ;;
    esac
  done <"$manifest_file"

  for key in "${expected_keys[@]}"; do
    [[ "$seen" == *"|$key|"* ]] || manifest_die "missing manifest key: $key" || return
  done
  [[ "$MANIFEST_CREATED_AT_UTC" =~ ^[0-9]{8}T[0-9]{6}Z$ ]] || \
    manifest_die 'invalid created_at_utc in manifest' || return
}

assert_manifest_target() {
  local project=$1 compose_file=$2 db_service=$3 database=$4 media_service=$5 media_path=$6
  local mismatches=()
  [[ "$project" == "$MANIFEST_COMPOSE_PROJECT" ]] || mismatches+=(compose_project)
  [[ "$compose_file" == "$MANIFEST_COMPOSE_FILE" ]] || mismatches+=(compose_file)
  [[ "$db_service" == "$MANIFEST_DATABASE_SERVICE" ]] || mismatches+=(database_service)
  [[ "$database" == "$MANIFEST_DATABASE_NAME" ]] || mismatches+=(database_name)
  [[ "$media_service" == "$MANIFEST_MEDIA_SERVICE" ]] || mismatches+=(media_service)
  [[ "$media_path" == "$MANIFEST_MEDIA_PATH" ]] || mismatches+=(media_path)
  ((${#mismatches[@]} == 0)) || {
    manifest_die "explicit target does not match backup manifest: ${mismatches[*]}"
    return 1
  }
}
