#!/bin/sh
set -eu

validate_origin() {
  value=$1
  name=$2
  if ! printf '%s\n' "$value" | grep -Eq '^https://[A-Za-z0-9.-]+(:[0-9]{1,5})?$'; then
    printf '%s must contain exact HTTPS origins only (no wildcard, path, query, or directive)\n' "$name" >&2
    exit 1
  fi
}

upload_count=0
for origin in ${MEDIA_UPLOAD_ORIGIN:-}; do
  validate_origin "$origin" MEDIA_UPLOAD_ORIGIN
  upload_count=$((upload_count + 1))
done
if [ "$upload_count" -gt 1 ]; then
  printf 'MEDIA_UPLOAD_ORIGIN accepts at most one origin\n' >&2
  exit 1
fi

for origin in ${MEDIA_PUBLIC_ORIGINS:-}; do
  validate_origin "$origin" MEDIA_PUBLIC_ORIGINS
done
