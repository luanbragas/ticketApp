# Testes de carga (PLAN.md M9)

Scripts [k6](https://k6.io) para os dois picos da festa: o link caindo no grupo do WhatsApp e a abertura de lote.
Rodam pelo Docker, sem instalar nada:

```bash
# página do evento (Next em modo produção: pnpm build && pnpm start)
docker run --rm -v "$PWD/infra/load:/scripts" grafana/k6 run \
  -e WEB=http://host.docker.internal:3001 -e API=http://host.docker.internal:8080 \
  -e SLUG=<slug> -e VUS=200 /scripts/event-page.js

# abertura de lote (API com FESTA_RATELIMIT_ENABLED=false: todo mundo sai do mesmo IP)
docker run --rm -v "$PWD/infra/load:/scripts" grafana/k6 run \
  -e API=http://host.docker.internal:8080 -e SLUG=<slug> -e BATCH=<id do lote> -e BUYERS=500 \
  /scripts/batch-opening.js
```

`batch-opening.js` cria pedidos de verdade (reservas de 10 min). Use um evento de teste.

## Resultado local — 2026-10-11

Notebook de desenvolvimento (k6, Docker com Postgres 16, API em `spring-boot:run` e Next na mesma máquina).
Serve para achar gargalo e comparar mudanças, **não** como capacidade de produção: repetir em staging.

**Abertura de lote** — 500 compradores ao mesmo tempo, 1 ingresso cada, lote de 100:

| | reservas | esgotado | outras respostas | p95 do pedido |
|---|---|---|---|---|
| reserva no meio da transação | 100 | 400 | 0 | 4,3 s |
| reserva no fim da transação (atual) | 100 | 400 | 0 | 1,9–2,2 s |

- Nenhum ingresso além da capacidade em nenhuma rodada (`sold + reserved = capacity` no banco).
- Gargalo: a linha do lote fica travada do `UPDATE` da reserva até o commit, e todos os pedidos do mesmo lote fazem
  fila nela. Mover a reserva para depois dos inserts do pedido encurtou a trava pela metade. Aumentar o pool de
  conexões (10 → 30) piorou (mais gente brigando pela mesma linha).
- Pedido isolado: ~70 ms com sucesso, ~20 ms quando esgotado (sai antes de travar, pelo resumo).

**Página do evento** — 200 usuários abrindo a página e os dados públicos, com 1–3 s entre cliques (~90 req/s):

| | p95 | falhas |
|---|---|---|
| API `GET /public/events/{slug}` | 99 ms | 0% |
| Página `/e/{slug}` (Next, um processo) | 2,4 s | 0% |

- A API sobra. O limite é a renderização no Next: um processo Node (~45 páginas/s neste notebook, dividindo CPU
  com o resto). Os dados do evento já vêm do cache (`"use cache"`, 1 min).
- Antes do piloto, em staging: medir com o Next no provedor final; se ainda for o gargalo, cache curto do HTML na
  borda (Cloudflare) para `/e/*` ou mais instâncias.
