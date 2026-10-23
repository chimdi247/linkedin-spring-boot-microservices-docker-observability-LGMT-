#!/bin/sh
# One-shot job: create the S3 buckets used by the app and by Mimir / Loki / Tempo (idempotent).
set -e
until mc alias set local "http://minio:9000" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null 2>&1; do
  echo "waiting for minio ..."; sleep 2
done
for b in "$MEDIA_BUCKET" mimir-blocks mimir-ruler mimir-alertmanager loki-chunks loki-ruler tempo-traces; do
  mc mb --ignore-existing "local/$b"
done
# uploaded avatars / post images are served to browsers through nginx (/media/) -> anonymous read
mc anonymous set download "local/$MEDIA_BUCKET"
echo "buckets ready"
