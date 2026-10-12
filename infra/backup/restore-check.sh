#!/usr/bin/env bash
# Ensaio de restauração (SECURITY.md: "backup restaurado com sucesso"): sobe um Postgres descartável, restaura o
# arquivo e confere que o banco está inteiro. Não toca no banco de origem.
#
#   infra/backup/restore-check.sh backups/festa-20261012T000000Z.dump
set -euo pipefail

DUMP="${1:?informe o arquivo .dump}"
PG_IMAGE="${PG_IMAGE:-postgres:16}"
NAME="festa-restore-check-$$"

cleanup() { docker rm -f "$NAME" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker run -d --name "$NAME" -e POSTGRES_PASSWORD=check -e POSTGRES_DB=festa "$PG_IMAGE" >/dev/null
for _ in $(seq 1 30); do
  docker exec "$NAME" pg_isready -U postgres -d festa >/dev/null 2>&1 && break
  sleep 1
done
# O initdb reinicia o servidor uma vez: espera o banco aceitar consultas de verdade.
for _ in $(seq 1 30); do
  docker exec "$NAME" psql -U postgres -d festa -c "SELECT 1" >/dev/null 2>&1 && break
  sleep 1
done

START=$(date +%s)
docker exec -i "$NAME" pg_restore --no-owner --no-privileges --exit-on-error -U postgres -d festa < "$DUMP"
echo "Restaurado em $(( $(date +%s) - START )) s"

psql() { docker exec "$NAME" psql -U postgres -d festa -tA -c "$1"; }
echo "Última migration: $(psql "SELECT version || ' ' || description FROM flyway_schema_history WHERE success ORDER BY installed_rank DESC LIMIT 1")"
for table in users organizations events ticket_batches orders tickets payments refunds ledger_entries consent_records audit_logs; do
  printf '%-16s %s\n' "$table" "$(psql "SELECT count(*) FROM $table")"
done
# Regras que o banco garante continuam valendo depois de restaurar.
BAD=$(psql "SELECT count(*) FROM ticket_batches WHERE sold + reserved > capacity")
[ "$BAD" = "0" ] || { echo "Estoque inconsistente em $BAD lote(s)"; exit 1; }
echo "OK"
