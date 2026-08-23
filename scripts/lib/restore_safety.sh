#!/usr/bin/env bash

restore_safety_die() {
  printf 'restore safety: %s\n' "$*" >&2
  return 1
}
validate_media_archive() {
  local archive=$1 destination=$2 member listing
  [[ -f "$archive" ]] || restore_safety_die "archive not found: $archive" || return
  [[ ! -e "$destination" ]] || restore_safety_die "validation destination exists: $destination" || return

  while IFS= read -r member; do
    case "$member" in
      /*|../*|*/../*|*/..) restore_safety_die "unsafe archive path: $member" || return ;;
    esac
  done < <(tar -tzf "$archive")

  listing=$(tar -tvzf "$archive") || restore_safety_die 'unable to list media archive' || return
  while IFS= read -r line; do
    case "$line" in
      l*|h*|*' link to '*|*' -> '*)
        restore_safety_die 'symbolic links are not allowed in media archive' || return
        ;;
    esac
  done <<<"$listing"

  mkdir -p -- "$destination"
  if ! tar -xzf "$archive" -C "$destination"; then
    find "$destination" -mindepth 1 -delete 2>/dev/null || true
    rmdir "$destination" 2>/dev/null || true
    restore_safety_die 'unable to extract media archive' || return
  fi
  if [[ -n $(find "$destination" -type l -print -quit) ]]; then
    find "$destination" -mindepth 1 -delete 2>/dev/null || true
    rmdir "$destination" 2>/dev/null || true
    restore_safety_die 'symbolic links are not allowed in media archive' || return
  fi
}

run_guarded_step() {
  local forward=$1 rollback=$2 status
  shift 2
  if "$forward" "$@"; then
    return 0
  else
    status=$?
  fi
  "$rollback" || printf 'restore safety: rollback failed; manual recovery is required\n' >&2
  return "$status"
}
