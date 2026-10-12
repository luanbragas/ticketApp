#!/usr/bin/env bash
# Backup lógico do Postgres (formato custom do pg_dump, comprimido). Lê a conexão de DATABASE_URL
# (postgres://usuario:senha@host:porta/banco) e grava em ./backups/festa-<data>.dump.
#
#   DATABASE_URL=postgres://festa:festa@localhost:15432/festa infra/backup/backup.sh
#
# Roda o pg_dump do Docker (mesma versão do servidor), sem precisar instalar o cliente.
# Em produção o provedor do Postgres gerenciado faz o backup contínuo; este é o backup extra, fora do provedor.
set -euo pipefail

: "${DATABASE_URL:?defina DATABASE_URL}"
OUT_DIR="${OUT_DIR:-backups}"
PG_IMAGE="${PG_IMAGE:-postgres:16}"
mkdir -p "$OUT_DIR"
FILE="festa-$(date -u +%Y%m%dT%H%M%SZ).dump"

# localhost dentro do container é o próprio container: troca pelo host do Docker.
URL="${DATABASE_URL/@localhost:/@host.docker.internal:}"
URL="${URL/@127.0.0.1:/@host.docker.internal:}"

docker run --rm -e URL="$URL" "$PG_IMAGE" sh -c 'pg_dump --format=custom --no-owner --no-privileges "$URL"' \
  > "$OUT_DIR/$FILE"

echo "$OUT_DIR/$FILE ($(du -h "$OUT_DIR/$FILE" | cut -f1))"
