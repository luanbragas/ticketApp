# ARCHITECTURE.md

## Visão geral

```
Cloudflare (DNS, CDN, WAF, Turnstile)
   ├── apps/web  Next.js  (área pública + painel + PWA de check-in)
   └── apps/api  Spring Boot  (monólito modular)
          ├── PostgreSQL        (fonte da verdade)
          ├── Cloudflare R2     (flyers e imagens)
          └── Externos: Mercado Pago · E-mail · Sentry
```

Princípio: **começar simples**. Sem microserviços, sem Redis, sem fila externa no MVP. Cada peça nova exige um ADR justificando.

## Módulos do backend

| Módulo | Responsabilidade |
|---|---|
| `identity` | Usuários, login, sessões, link mágico |
| `organization` | Organizações, membros, papéis, conta de recebimento |
| `event` | Eventos, mídia, lineup, publicação |
| `ticketing` | Tipos, lotes, estoque, ingressos emitidos, token de QR |
| `order` | Carrinho, pedido, reserva, expiração, limite por CPF |
| `payment` | Integração com gateway, webhook, split, estorno |
| `promoter` | Promoters, links, atribuição, comissões |
| `checkin` | Portarias, validação, sincronização offline |
| `finance` | Lançamentos, taxas, repasses |
| `notification` | E-mails e, no futuro, WhatsApp |
| `compliance` | Termos, consentimentos, audit log |

Regras:
- Cada módulo expõe um pacote `api` (interfaces e DTOs públicos). O resto é interno.
- Um módulo **não** acessa repositórios nem tabelas de outro.
- Efeitos colaterais entre módulos usam **eventos de domínio** gravados no outbox.

## Multi-tenant

Banco compartilhado. Toda tabela de dado da organização tem `organization_id`.
O contexto de organização vem do usuário autenticado + vínculo em `organization_members` + dono do recurso. Ver SECURITY.md.

## Máquinas de estado

**Pedido**
```
PENDING_PAYMENT → PAID | EXPIRED | FAILED
PAID            → REFUNDED | PARTIALLY_REFUNDED | CHARGEBACK
```

**Ingresso** (só existe após pagamento)
```
VALID      → CHECKED_IN | TRANSFERRED (gera novo VALID) | CANCELLED
CHECKED_IN → VALID   (somente desfazer check-in, com audit log)
```

**Evento**
```
DRAFT → PUBLISHED → ENDED
DRAFT | PUBLISHED → CANCELLED
```

Toda transição é feita por método de domínio que valida o estado de origem. Transição inválida = exceção.

## Fluxos críticos

### Estoque (sem overselling)
```sql
UPDATE ticket_batches
   SET reserved = reserved + :qty
 WHERE id = :batch_id
   AND status = 'ON_SALE'
   AND sold + reserved + :qty <= capacity;
-- 0 linhas = indisponível
```
- Criação do pedido e reserva na **mesma transação**.
- Pagamento confirmado: `reserved -= qty; sold += qty`.
- Expiração (job a cada minuto): `reserved -= qty`, pedido → `EXPIRED`.
- Virada de lote avaliada após cada venda/expiração e por job de data.

### Pagamento
```
1. POST /orders              → pedido PENDING_PAYMENT + reserva
2. POST /orders/{id}/payment → cobrança no gateway (idempotency key = order id, split)
3. Comprador paga
4. POST /webhooks/mercadopago
     a. valida assinatura
     b. consulta o pagamento na API do gateway (não confia no payload)
     c. upsert em payments por gateway_payment_id (idempotente)
     d. se aprovado: order.markPaid() → evento OrderPaid no outbox
5. Worker do outbox: emite ingressos, envia e-mail, atualiza promoter
6. Web acompanha GET /orders/{id}/status (polling)
```
Pagamento aprovado com pedido `EXPIRED`: tenta reservar de novo; se não houver estoque, estorna e notifica.

### Check-in offline
```
Antes:   operador abre o PWA → baixa lista da portaria (hash do token, nome, tipo, status)
Durante: leitura valida no IndexedDB → grava check-in local → fila
         online: envia em lote POST /checkins/sync
Conflito: vale o check-in com horário mais antigo; duplicados vão para relatório
```

### Outbox
- Tabela `outbox_events` gravada na mesma transação da mudança de estado.
- Worker lê eventos pendentes (`FOR UPDATE SKIP LOCKED`), processa e marca como enviado.
- Consumidores são idempotentes (o mesmo evento pode ser processado duas vezes).

## URLs

| Recurso | URL |
|---|---|
| Evento | `/e/{slug}` |
| Link de promoter | `/e/{slug}?p={code}` |
| Checkout | `/e/{slug}/checkout` |
| Ingresso | `/t/{token}` |
| Organização | `/o/{slug}` |
| Painel | `/painel/...` |
| Check-in | `/checkin/{eventId}` |

## Ambientes

| Ambiente | Uso |
|---|---|
| local | docker compose + Mercado Pago sandbox |
| staging | deploy automático da `main`, sandbox |
| production | deploy por tag, credenciais reais |

## Decisões (ADRs)

Formato: contexto → decisão → consequências. Registrar aqui em ordem.

- **ADR-000 — Monólito modular.** Equipe pequena; microserviços adicionariam operação sem benefício. Extração futura possível graças às fronteiras de módulo.
- **ADR-001 — Autenticação: sessão no servidor em cookie.** *Aceito em 2026-10-07.*
  - **Contexto:** web (Next.js) e API (Spring) ficam no mesmo domínio registrável (`festa.com` / `api.festa.com`). Precisamos de logout imediato, revogar sessão de membro removido da organização e não expor token a JavaScript. A arquitetura evita Redis no MVP.
  - **Decisão:** Spring Security com sessão no servidor guardada no Postgres (**Spring Session JDBC**). Cookie de sessão `HttpOnly; Secure; SameSite=Lax` no domínio pai (`.festa.com`). CSRF ativo nos métodos de escrita com `CookieCsrfTokenRepository` (cookie `XSRF-TOKEN` lido pela web e devolvido no header `X-XSRF-TOKEN`). Webhooks ficam fora do CSRF porque são validados por assinatura. Login com senha, Google (OIDC) e link mágico terminam todos criando a mesma sessão. Sem JWT.
  - **Consequências:** logout e revogação valem na hora (basta apagar a sessão). Nada de token em `localStorage`, o que reduz o estrago de um XSS. Web e API precisam continuar no mesmo site; em dev, `localhost:3000` e `localhost:8080` já contam como mesmo site. Server Components do Next que chamam a API repassam o cookie do usuário. O M1 cria a migration com as tabelas do Spring Session. O tempo de inatividade é configurável, e a sessão longa do operador de check-in é tratada no M8.
- **ADR-002 — Acesso a dados: JPA + SQL nativo.** *Aceito em 2026-10-07.*
  - **Contexto:** a maior parte do domínio é CRUD de agregados com regras e máquinas de estado. As partes críticas (reserva de estoque, relatórios, outbox com `SKIP LOCKED`) precisam de SQL exato e previsível.
  - **Decisão:** **Spring Data JPA** (Hibernate) para agregados e CRUD, com entidades sem setters públicos de status. **SQL nativo via `JdbcClient`** para o `UPDATE` condicional de estoque, relatórios/dashboards e o worker do outbox. Sem jOOQ por enquanto. Flyway é dono do esquema; o Hibernate só valida (`ddl-auto: validate`) e `open-in-view` fica desligado. IDs UUIDv7 gerados na aplicação. Dinheiro mapeado como `long` em centavos.
  - **Consequências:** uma ferramenta só para o caso comum e SQL explícito onde há dinheiro e concorrência. SQL nativo não é verificado em compilação, então toda consulta nativa tem teste de integração com Testcontainers. Repositórios continuam internos ao módulo (`infra`). Se relatórios crescerem muito, reavaliar jOOQ em novo ADR.
- **ADR-003 — Gateway e split.** *Pendente.* Mercado Pago com OAuth do produtor; plataforma não custodia dinheiro.
- **ADR-004 — Comprador ganha conta no primeiro acesso por link mágico.** *Aceito em 2026-10-07.*
  - **Contexto:** o comprador não precisa de cadastro para comprar e acessa "Meus ingressos" por link mágico (SECURITY.md). Era preciso decidir se o link cria uma conta ou abre uma sessão separada "só por e-mail".
  - **Decisão:** o primeiro clique num link mágico cria um usuário sem nome e sem senha, com e-mail já verificado; os seguintes entram na mesma conta. Há um único tipo de sessão (ADR-001) e pedidos se ligam ao comprador por `orders.buyer_user_id`. O link é um token aleatório de 32 bytes no **fragmento** da URL (`/link-magico#token=...`, não vai para logs nem `Referer`), guardado só como SHA-256, vale 15 min e é consumido por um `UPDATE ... RETURNING` atômico. O pedido de link sempre responde 202 e há limite de 5 links por e-mail por hora. Se o link confirmar um e-mail de conta **não verificada** que tinha senha, a senha é descartada e as sessões daquela conta são encerradas (proteção contra conta pré-criada por terceiro).
  - **Consequências:** `users.name` passa a ser opcional. A tabela `users` cresce com compradores, e os termos de uso precisam dizer que a conta é criada no primeiro acesso. Comprador pode depois definir senha ou virar produtor sem migração de dados. Falta limite por IP (Bucket4j, checklist do SECURITY.md) e limpeza periódica de links antigos.

- **ADR-005 — A página do evento veste a cor e o formato do flyer.** *Aceito em 2026-10-08.*
  - **Contexto:** o design aprovado (direção G, "Grade da noite") é preto, branco e uma única cor de destaque. Cada festa precisa ter a própria cara sem o produtor poder deixar a página ilegível, e flyers chegam em story (9:16), feed (4:5) ou foto deitada.
  - **Decisão:** a cor de destaque é extraída do flyer no navegador do produtor durante o wizard (prévia) e salva no evento em `events.accent_color` (`#rrggbb`), editável. Na hora de salvar, a cor é ajustada para ter contraste ≥ 4,5:1 sobre o preto; sem cor forte no flyer, fica nula e a página usa o verde da plataforma. O texto sobre a cor é preto ou branco, o que tiver mais contraste. `event_media` guarda `width` e `height` do flyer, e a capa escolhe o layout pela proporção (≥ 1,5 story; 1,1–1,5 feed; < 1,1 deitada, com fundo desfocado). O painel do produtor usa sempre o verde da plataforma.
  - **Consequências:** todas as telas do comprador usam o mesmo valor salvo, sem cálculo no celular de quem compra. O backend valida o formato da cor; a regra de contraste fica na web e é coberta por teste unitário. Trocar o flyer sugere a nova cor, mas não sobrescreve uma cor escolhida à mão.

- **ADR-006 — Virada de lote por tipo, só para a frente, e cota de meia como mínimo legal.** *Aceito em 2026-10-08.*
  - **Contexto:** o M3 pede que o lote vire sozinho "quando esgota ou chega a data, o que vier primeiro", que lote encerrado não reabra, e uma cota de meia-entrada por evento (padrão 40%, Lei 12.933/2013).
  - **Decisão:**
    - Cada tipo de ingresso tem uma fila de lotes (`position`). No máximo um lote à venda por tipo (restrição `EXCLUDE` adiada para o commit, porque a virada fecha um e abre outro na mesma transação).
    - O lote à venda vira quando os **pagos** (`sold`) chegam à capacidade ou quando chega `sales_end_at`. Reserva não vira lote: se a reserva expira, o estoque volta para o mesmo lote e ninguém paga o preço do próximo lote antes da hora.
    - O próximo lote abre na hora, salvo se tiver `sales_start_at` no futuro; nesse caso o tipo espera e os lotes de trás não passam na frente. Lote cuja virada passou sem abrir é encerrado.
    - `SOLD_OUT` e `CLOSED` são finais: o lote não é editado nem reaberto. O produtor pode encerrar à mão, e o próximo abre.
    - A regra é uma função pura (`BatchRollover`) usada em três lugares: depois de toda edição (com os lotes do evento travados com `SELECT ... FOR UPDATE`), no job de data a cada minuto e na leitura (painel e página pública mostram o status já virado mesmo antes do job rodar). Sem ShedLock por enquanto: rodar o job em duas instâncias só repete trabalho.
    - Meia-entrada é um tipo marcado como meia, com lotes próprios. A cota é o **mínimo** de ingressos de meia sobre o total oferecido (meia + inteira; de lote encerrado conta só o vendido ou reservado). O painel mostra se o evento cumpre a cota, mas não bloqueia a publicação.
  - **Consequências:** o M4 reserva com `UPDATE` condicional em lote `ON_SALE` e roda a virada depois de cada pagamento ou expiração. `sold` e `reserved` não são gravados pela entidade JPA, só por SQL atômico. Bloquear a publicação de quem não cumpre a cota fica para decisão de produto.

- **ADR-007 — Pedido sem cadastro: taxa por cima, chave do navegador e CPF cifrado.** *Aceito em 2026-10-08.*
  - **Contexto:** o comprador compra sem conta (PLAN M4), o produtor recebe o preço cheio e não pode haver venda acima do estoque nem pedido em dobro em rede ruim.
  - **Decisão:**
    - **Taxa de serviço** paga pelo comprador, por cima do preço, por ingresso: `round(preço × SERVICE_FEE_BPS / 10000)` com piso `SERVICE_FEE_MIN_CENTS`. Valor em variável de ambiente (padrão 10%, sem piso) até a decisão final antes do piloto. Preço e taxa congelam no item do pedido.
    - **Reserva** no mesmo `UPDATE` atômico da ARCHITECTURE §Estoque, na transação que cria o pedido, lote a lote em ordem de id (sem espera circular). O `UPDATE` também confere a janela de venda, para um lote cuja virada passou não vender até o job rodar. Pedido vence em 10 min; o job (a cada 30 s, `FOR UPDATE SKIP LOCKED`) expira e devolve a reserva.
    - **Chave do pedido:** o navegador gera um `Idempotency-Key` aleatório (≥ 32 caracteres). A mesma chave devolve o mesmo pedido e é o segredo para ler o status (`X-Order-Key`). O banco guarda só o SHA-256 (`orders.access_key_hash`).
    - **Limite por CPF** conta os ingressos de pedidos pagos ou aguardando pagamento do **CPF do comprador** no evento. Dois pedidos do mesmo CPF ao mesmo tempo passam em fila (`pg_advisory_xact_lock`).
    - **CPF** de comprador e titulares: AES-256-GCM (`PERSONAL_DATA_KEY`) + HMAC-SHA256 (`PERSONAL_DATA_HASH_KEY`) para busca. A API só devolve mascarado.
    - **Meia:** o titular declara o benefício (estudante, PCD, jovem de baixa renda, pessoa idosa); o documento é conferido na entrada. **18+:** o comprador declara pelos titulares quando o evento é para maiores.
  - **Consequências:** o M5 cobra o `total_cents` do pedido e, no webhook, faz `reserved -= n; sold += n` e roda a virada. O aceite dos termos fica no pedido (`terms_version`, `terms_accepted_at`) até o M8 criar `consent_records` e o texto jurídico. Rate limit e Turnstile no `POST /public/orders` ficam para o M8.

- **ADR-008 — Emissão por outbox e token do QR remontável.** *Aceito em 2026-10-08.*
  - **Contexto:** o ingresso só nasce depois do pagamento (CLAUDE.md regra 6), o e-mail não pode se perder nem duplicar ingresso, e o comprador precisa ver o QR de novo no e-mail e em "Meus ingressos" sem o banco guardar o token (SECURITY.md).
  - **Decisão:**
    - `OrderPayments.confirmPaid` (chamado só pelo webhook do M5) marca o pedido pago, transforma reserva em venda (`reserved -= n; sold += n`), roda a virada do lote e grava `OrderPaid` no `outbox_events`, tudo numa transação. O evento leva os dados dos titulares (CPF cifrado), para o ticketing não ler as tabelas de pedido.
    - O worker (`shared/outbox`, a cada 2 s, `FOR UPDATE SKIP LOCKED`, uma transação por evento, espera crescente e `FAILED` depois de 10 tentativas) entrega `OrderPaid` ao emissor, que cria um ingresso por item (`order_item_id UNIQUE`, pula o que já existe) e publica `TicketsIssued`. O e-mail é outra entrega: se o provedor cair, só o e-mail é refeito.
    - **Token do QR** = `base64url(HMAC-SHA256(TICKET_TOKEN_KEY, nonce))`, com nonce aleatório de 32 bytes por ingresso. O banco guarda o nonce e o SHA-256 do token; sem a chave, quem lê o banco não monta um QR. Com a chave, o sistema remonta o token para o e-mail e "Meus ingressos". Transferir ou cancelar troca o nonce.
    - "Meus ingressos" lista pelo e-mail do comprador (`tickets.buyer_email`) e só para conta com e-mail verificado (link mágico, ADR-004).
  - **Consequências:** trocar `TICKET_TOKEN_KEY` invalida todos os QRs emitidos. O e-mail pode, raramente, sair duas vezes (enviou e a transação caiu depois); perder o e-mail não acontece. Ingresso de titular que não é o comprador só aparece para o comprador até existir transferência.
