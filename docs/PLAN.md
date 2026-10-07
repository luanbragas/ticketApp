# PLAN.md

Plano de execução. Cada marco termina com algo **demonstrável**. Marque `[x]` ao concluir.
Regra: só começar um marco quando o anterior estiver pronto (exceto onde indicado "paralelo").

**Objetivo da Fase 1:** rodar uma festa real do começo ao fim (publicar → vender → receber → entrar).

---

## Fase 0 — Fundação

**Pronto quando:** `docker compose up` + api + web sobem localmente, CI verde, deploy de "hello world" em staging.

- [x] Criar monorepo (`apps/web`, `apps/api`, `infra`, `docs`)
- [x] `infra/docker-compose.yml` com Postgres 16 e Mailpit
- [x] `apps/api`: Spring Boot + Flyway + Actuator + Testcontainers + estrutura de módulos (ver BACKEND.md)
- [x] `apps/web`: Next.js + TS + Tailwind + shadcn/ui + ESLint/Prettier
- [x] CI (GitHub Actions): lint, typecheck, testes da web e da api
- [ ] Sentry na web e na api
- [ ] Ambiente de staging (web + api + Postgres gerenciado) com deploy automático da `main`
- [ ] Conta Mercado Pago de testes + app criado no painel de desenvolvedor
- [x] **ADR-001** autenticação (sessão em cookie vs JWT)
- [x] **ADR-002** acesso a dados (JPA vs jOOQ)
- [ ] **ADR-003** confirmar split do Mercado Pago (marketplace/OAuth, taxas, prazos, chargeback)

## Fase 1 — MVP

### M1 · Identidade e organização
**Pronto quando:** produtor cria conta, faz login, cria organização e convida um membro.

- [x] Tabelas `users`, `user_identities`, `organizations`, `organization_members` (migration)
- [x] Cadastro e login por e-mail + senha (Argon2id)
- [ ] Login com Google (OIDC) — aguardando Client ID do Google Cloud
- [x] Link mágico por e-mail (usado também pelo comprador em "Meus ingressos")
- [x] Criar organização (nome, slug)
- [ ] Logo da organização — depende do upload para o R2 (M2)
- [x] Papéis fixos por organização: OWNER, ADMIN, MANAGER, PROMOTER, CHECKIN_OPERATOR
- [x] Guarda de autorização + filtro por `organization_id` + **teste de isolamento entre organizações**
- [x] Convidar membro por e-mail com papel
- [x] Web: telas de login, cadastro, criar organização, shell do painel (menu lateral desktop / barra inferior mobile)

### M2 · Eventos
**Pronto quando:** produtor publica um evento e a página pública abre bonita no celular e gera preview no WhatsApp.

- [ ] Tabelas `events`, `event_media`, `event_lineup`
- [ ] Upload de flyer para R2 via URL pré-assinada (validar tipo e tamanho)
- [ ] CRUD de evento com estados DRAFT → PUBLISHED → ENDED / CANCELLED
- [ ] Wizard web: Informações → Aparência → Ingressos → Configurações → Revisar e publicar
- [ ] Página pública `/e/{slug}` com SSR, metadados Open Graph e botão "Comprar" fixo
- [ ] Lista "Meus eventos" com filtros
- [ ] Classificação etária (18+ obrigatório quando open bar)

### M3 · Ingressos e lotes
**Pronto quando:** evento com 2 tipos e 3 lotes vira de lote sozinho por quantidade e por data.

- [ ] Tabelas `ticket_types`, `ticket_batches`
- [ ] CRUD de tipos e lotes (preço em centavos, capacidade, janela de venda, limite por pessoa, visibilidade)
- [ ] Regra de virada: esgotou **ou** chegou a data (o que vier primeiro); lote encerrado não reabre
- [ ] Cota de meia-entrada por evento (padrão 40%) e lotes de meia
- [ ] Endpoint público de disponibilidade por evento
- [ ] Testes unitários das regras de virada e cota

### M4 · Pedidos e reserva
**Pronto quando:** 500 compras concorrentes num lote de 100 resultam em exatamente 100 reservas.

- [ ] Tabelas `orders`, `order_items`
- [ ] Seleção de ingressos (web) com subtotal, taxa e total calculados **no backend**
- [ ] Checkout em uma coluna: dados do comprador, titular de cada ingresso, documento de meia, declaração 18+, aceite de termos
- [ ] Criação de pedido `PENDING_PAYMENT` + reserva atômica (ver ARCHITECTURE.md §Estoque)
- [ ] Limite por CPF por evento
- [ ] Job que expira pedidos após 10 min e devolve estoque
- [ ] **Teste de concorrência** com Testcontainers (overselling = 0)

### M5 · Pagamentos
**Pronto quando:** compra PIX e cartão em sandbox gera pedido `PAID`, com split para a conta do produtor.

- [ ] Produtor conecta conta Mercado Pago (OAuth) → `payment_accounts`
- [ ] Criar cobrança PIX e cartão com split (taxa da plataforma) e idempotency key = id do pedido
- [ ] Tela PIX: QR, copia e cola, contador, status por polling
- [ ] Webhook: validar assinatura → consultar pagamento no gateway → processar idempotente → `payments`
- [ ] Caso de borda: pagamento confirmado após expiração (emite se houver estoque; senão estorna)
- [ ] Testes do webhook com payloads repetidos e fora de ordem

### M6 · Emissão de ingressos
**Pronto quando:** após pagar, o comprador recebe e-mail com ingresso e acessa "Meus ingressos" pelo link mágico.

- [ ] Tabela `tickets` + `outbox_events`
- [ ] Evento `OrderPaid` → emitir 1 ingresso por item com token assinado (HMAC)
- [ ] Página do ingresso `/t/{token}` com QR, titular, tipo, data, local, adicionar ao calendário
- [ ] E-mail transacional com ingresso (worker do outbox)
- [ ] "Meus ingressos" (próximos e histórico)

### M7 · Promoters (básico) — pode ser paralelo a M6
**Pronto quando:** venda feita pelo link de um promoter aparece atribuída a ele no painel.

- [ ] Tabelas `promoters`, `promoter_event_links`
- [ ] Criar promoter, gerar link `/e/{slug}?p={codigo}`, copiar e compartilhar no WhatsApp
- [ ] Atribuição: cookie de 7 dias, último clique, gravado em `orders.promoter_id`
- [ ] Lista de promoters com ingressos e receita; promoter vê só as próprias vendas

### M8 · Operação do evento
**Pronto quando:** check-in de 50 ingressos funciona com o celular em modo avião e sincroniza depois.

- [ ] Dashboard: vendas, vendidos/capacidade, check-ins, gráfico por dia
- [ ] Participantes: busca, filtros, status de check-in
- [ ] Tabela `checkins`; check-in online com resultados válido / já utilizado / inválido
- [ ] Busca manual por nome/CPF na portaria
- [ ] PWA de check-in: baixar lista, validar offline (IndexedDB), fila de sincronização, conflitos
- [ ] Desfazer check-in (ADMIN) com audit log

### M9 · Financeiro, compliance e lançamento
**Pronto quando:** festa piloto realizada.

- [ ] Financeiro básico: bruto, taxas, comissões calculadas, líquido, repassado / a receber
- [ ] Termos de uso e política de privacidade versionados + `consent_records`
- [ ] Reembolso manual pelo admin (estorna no gateway, cancela ingresso)
- [ ] Landing da plataforma
- [ ] Revisão do checklist de SECURITY.md
- [ ] Teste de carga da página do evento e da abertura de lote
- [ ] Deploy de produção, domínio, backups do Postgres testados
- [ ] Validação jurídica (meia-entrada, reembolso, termos)
- [ ] **Festa piloto** com uma atlética

---

## Fase 2 — Operação
Portarias e operadores · Cortesias · Lista VIP com importação CSV/Excel · Cupons · Permissões granulares · Exportação CSV

## Fase 3 — Growth
Comissões automáticas · Painel e ranking do promoter · Links de campanha (UTM) · Meta Pixel e GA4 · Relatórios por canal

## Fase 4 — Financeiro
Extrato completo · Reembolso e transferência pelo painel · Chargebacks · Painel admin da plataforma

## Fase 5 — Ecossistema
Explorar eventos · Perfis de atléticas · Seguir organizações · Notificações · Wallet · Conexão entre universidades e cursos · Liga entre atléticas
