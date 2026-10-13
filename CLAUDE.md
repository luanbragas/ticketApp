# CLAUDE.md

Contexto para o Claude Code neste repositório. Leia este arquivo inteiro antes de qualquer tarefa.

## O que é o projeto

**FESTA** (nome provisório) é uma plataforma web de venda de ingressos e gestão de festas universitárias, atléticas e produtores independentes.

- **Comprador:** descobre a festa, compra via PIX/cartão sem cadastro obrigatório e recebe o ingresso com QR Code.
- **Produtor:** cria o evento, configura tipos e lotes, distribui links de promoters, acompanha vendas e faz o check-in pelo celular.
- **Posicionamento:** experiência tipo POSH + operação tipo Sympla + foco em promoters e atléticas.

## Onde está cada coisa

| Documento | Conteúdo |
|---|---|
| `docs/PLAN.md` | Fases, marcos e tarefas. **Fonte da verdade do que fazer agora.** |
| `docs/ARCHITECTURE.md` | Visão geral, módulos, fluxos críticos, decisões (ADRs) |
| `docs/DATABASE.md` | Convenções e esquema do banco |
| `docs/SECURITY.md` | Regras de segurança e LGPD |
| `docs/FRONTEND.md` | Next.js: estrutura, rotas, padrões de UI |
| `docs/BACKEND.md` | Spring Boot: módulos, API, padrões, testes |

## Estrutura do repositório

```
/
├── CLAUDE.md
├── docs/
├── apps/
│   ├── web/        # Next.js (área pública + painel + check-in PWA)
│   └── api/        # Spring Boot (monólito modular)
├── infra/
│   └── docker-compose.yml   # Postgres local, Mailpit, S3Mock (no lugar do R2)
└── .github/workflows/       # CI
```

## Stack

- **Web:** Next.js (App Router) + React + TypeScript, Tailwind, shadcn/ui, TanStack Query, React Hook Form + Zod.
- **API:** Java 21, Spring Boot, Spring Security, Spring Data JPA, Flyway, Maven.
- **Banco:** PostgreSQL 16+.
- **Pagamentos:** Mercado Pago (PIX, cartão, split).
- **Arquivos:** Cloudflare R2 (S3). **E-mail:** provedor transacional (Mailpit em dev).
- **Erros:** Sentry. **Testes:** JUnit 5 + Testcontainers (api), Vitest + Playwright (web).

## Comandos

```bash
# infra local
docker compose -f infra/docker-compose.yml up -d

# api
cd apps/api && ./mvnw spring-boot:run
cd apps/api && ./mvnw verify            # testes (Testcontainers sobe Postgres)

# web
cd apps/web && pnpm install && pnpm dev
cd apps/web && pnpm lint && pnpm typecheck && pnpm test
```

## Regras inegociáveis

1. **Dinheiro em centavos** (`long` / `BIGINT`). Nunca `double`, `float` ou `BigDecimal` solto em entidade de valor.
2. **Datas em `timestamptz`** / `Instant` no backend. Exibição em `America/Sao_Paulo`.
3. **Isolamento por organização:** toda consulta de dado de organização filtra por `organization_id`, derivado do usuário autenticado e do dono do recurso. **Nunca** do corpo da requisição.
4. **Pagamento só é confirmado por webhook** validado + consulta ao gateway. O frontend apenas acompanha status.
5. **Estoque:** reserva via `UPDATE` condicional atômico no Postgres. Postgres é a fonte da verdade.
6. **Pedido ≠ ingresso.** Ingresso só nasce após pagamento. Use as máquinas de estado de `ARCHITECTURE.md`; nada de `used = true`.
7. **Módulos não leem tabelas de outros módulos.** Comunicação por serviço público do módulo ou evento de domínio.
8. **QR Code** carrega token opaco assinado. Nunca ID sequencial nem dado pessoal.
9. **CPF** criptografado em repouso e mascarado nas respostas, exceto onde explicitamente necessário.
10. **Segredos** só em variáveis de ambiente. Nunca commitar `.env`.

## Convenções

- Código, nomes de tabelas, classes e commits em **inglês**. Textos de UI e documentação em **português**.
- Commits no padrão Conventional Commits (`feat(ticketing): ...`).
- Toda mudança de banco é uma migration Flyway nova. Nunca editar migration já aplicada.
- Toda regra de negócio nova vem com teste. Fluxos de dinheiro e estoque com teste de integração.
- API segue as convenções de `BACKEND.md` (REST, erros em Problem Details).

## Como trabalhar

1. Abra `docs/PLAN.md` e pegue a **primeira tarefa não marcada** do marco atual.
2. Leia o(s) doc(s) da área da tarefa antes de codar.
3. Faça a menor mudança que completa a tarefa, com testes.
4. Rode lint, typecheck e testes antes de concluir.
5. Marque a tarefa como feita no `PLAN.md` e, se tomou uma decisão de arquitetura, registre um ADR em `ARCHITECTURE.md`.
6. Se algo no pedido conflitar com as regras inegociáveis, **pare e pergunte**.
