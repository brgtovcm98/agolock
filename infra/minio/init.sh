#!/usr/bin/env bash
set -euo pipefail

# Bitnami MinIO 이미지에 포함된 mc를 사용합니다.
# 공식 minio/mc 이미지를 별도로 쓰지 않아 로컬 의존성을 줄입니다.
export MC_CONFIG_DIR="${MC_CONFIG_DIR:-/tmp/.mc}"
MINIO_ENDPOINT="${MINIO_ENDPOINT:-http://minio:9000}"
MINIO_ROOT_USER="${MINIO_ROOT_USER:?MINIO_ROOT_USER is required}"
MINIO_ROOT_PASSWORD="${MINIO_ROOT_PASSWORD:?MINIO_ROOT_PASSWORD is required}"
S3_BUCKET="${S3_BUCKET:-agolock-local}"

mc alias set local "$MINIO_ENDPOINT" "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD"
mc mb --ignore-existing "local/$S3_BUCKET"
mc anonymous set none "local/$S3_BUCKET"
mc ls "local/$S3_BUCKET"
