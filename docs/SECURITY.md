# SECURITY.md

## Autenticação

- Senhas com **Argon2id** (ou bcrypt custo ≥ 12). Nunca logar senha nem token.
- Login com Google via OIDC. Link mágico: token aleatório de 32 bytes, guardado só o hash, expira em 15 min, uso único.
- Sessão no servidor (Spring Session JDBC) em cookie `HttpOnly; Secure; SameSite=Lax` no domínio pai. CSRF ativo para métodos de escrita. Ver ADR-001.
- Sessão do operador de check-in pode durar a noite do evento; demais sessões expiram por inatividade.
- Comprador não precisa de cadastro: acesso aos ingressos por link mágico, que cria a conta no primeiro acesso (ADR-004).
- Link mágico que confirma e-mail de conta não verificada descarta a senha existente e encerra as sessões dela (conta pré-criada por terceiro).

## Autorização

- Papéis por organização (`organization_members.role`). Matriz definida até agora (completar a cada módulo):

  | Ação | OWNER | ADMIN | MANAGER | PROMOTER | CHECKIN_OPERATOR |
  |---|---|---|---|---|---|
  | Ver a organização | ✓ | ✓ | ✓ | ✓ | ✓ |
  | Ver equipe e convites pendentes | ✓ | ✓ | ✓ | — | — |
  | Convidar ADMIN | ✓ | — | — | — | — |
  | Convidar MANAGER, PROMOTER, CHECKIN_OPERATOR | ✓ | ✓ | — | — | — |
  | Ver eventos | ✓ | ✓ | ✓ | ✓ | ✓ |
  | Criar, editar, publicar e encerrar evento; enviar flyer | ✓ | ✓ | ✓ | — | — |
  | Cancelar evento | ✓ | ✓ | — | — | — |
  | Ver links e vendas de promoters do evento | ✓ | ✓ | ✓ | só o seu | — |
  | Criar e desativar link de promoter | ✓ | ✓ | ✓ | — | — |
  | Ver tipos e lotes | ✓ | ✓ | ✓ | ✓ | ✓ |
  | Criar, editar, encerrar e excluir tipos e lotes | ✓ | ✓ | ✓ | — | — |

  Ninguém é convidado como OWNER (um dono por organização). Convite vale 7 dias, uma vez, só para o e-mail convidado.
- Toda rota do painel passa por uma checagem central: `TenantGuard.requireRole(orgId, userId, roles...)` (módulo `organization.api`).
- **Organization ID nunca vem do corpo da requisição.** Carregue o recurso, compare `resource.organizationId` com as organizações do usuário. Recurso de outra organização → `404` (não `403`, para não revelar existência).
- PROMOTER vê só seus links e vendas. CHECKIN_OPERATOR vê só o necessário para validar (nome, tipo, status).
- Teste obrigatório: usuário da organização A tentando ler/alterar recurso da B em cada endpoint do painel.

## Pagamentos

- Confirmação **somente** via webhook com assinatura válida + consulta à API do gateway.
- Idempotência: `orders.idempotency_key` na criação; `payments.provider_payment_id UNIQUE` no webhook.
- Valores sempre recalculados no backend. O frontend nunca envia preço ou total.
- Tokens OAuth do produtor criptografados em repouso.

## QR Code

- Token = HMAC-SHA256(`TICKET_TOKEN_KEY`, nonce aleatório de 32 bytes), em base64url (ADR-008).
- Banco guarda só o nonce e o `token_hash`; sem a chave não se monta um token. QR não contém nome, CPF nem ID sequencial.
- Ingresso cancelado ou transferido invalida o token na hora.

## Abuso e disponibilidade

- Rate limit por IP (Bucket4j em memória no MVP, `RateLimitFilter`): login 10/min, cadastro 5/min, link mágico 5/min, consumir link 10/min, pedido 60/min, cotação 120/min, webhook 600/min; resposta 429 com `Retry-After`. Pedido e cotação são generosos porque muita gente compra pelo mesmo Wi-Fi do campus. Atrás de proxy, definir `FORWARD_HEADERS_STRATEGY=framework` (senão todos viram o IP do proxy). Validação de cupom entra junto com cupons.
- Cloudflare Turnstile no checkout em aberturas de lote.
- Upload: URL pré-assinada, só `image/jpeg|png|webp`, máximo 5 MB, nome gerado pelo servidor.

## Entrada e saída

- Validação com Bean Validation (api) e Zod (web). Rejeitar campos desconhecidos.
- Só consultas parametrizadas. Nada de concatenar SQL.
- CORS restrito aos domínios da web.
- Headers: API com CSP `default-src 'none'`, `nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer` (HSTS do Spring em HTTPS). Web com `nosniff`, `Referrer-Policy`, `Permissions-Policy` (câmera só na mesma origem), HSTS em produção e CSP em duas partes: `frame-ancestors`, `base-uri`, `form-action` e `object-src` valendo; a política completa em `Report-Only` até ser conferida no navegador.
- Erros em Problem Details sem stack trace nem detalhes internos.

## Dados pessoais

- CPF criptografado (AES-GCM, chave em env/KMS) + `cpf_hash` (HMAC) para busca. Exibir mascarado: `***.456.789-**`. Chaves: `PERSONAL_DATA_KEY` e `PERSONAL_DATA_HASH_KEY` (32 bytes, base64); as do `application-local.yml` são só de desenvolvimento.
- Pedido público: só quem tem a chave gerada pelo navegador (`X-Order-Key`) lê o pedido; chave errada responde 404. O banco guarda só o hash.
- Logs sem CPF, e-mail completo, telefone ou tokens.
- Segredos só em variáveis de ambiente / secret manager. `.env` no `.gitignore`.

## Auditoria

Gravar em `audit_logs`: reembolso, cortesia, desfazer check-in, alteração de preço/capacidade de lote, alteração de conta de recebimento, mudança de papel de membro, publicação e cancelamento de evento.

## LGPD

- Plataforma: controladora dos dados de conta; operadora dos dados de participantes tratados para o produtor (deixar claro nos termos).
- Bases legais: execução de contrato (compra); consentimento separado para marketing.
- Registrar aceite em `consent_records` com versão dos termos.
- Minimização: coletar só o necessário; promoter não vê CPF.
- Direitos do titular: exportação e anonimização sob pedido.
- Pixels de terceiros (Fase 3) só com aviso de cookies.

## Checklist antes do piloto

- [ ] Teste de isolamento entre organizações em todos os endpoints do painel
- [ ] Webhook rejeita assinatura inválida e processa repetidos sem duplicar
- [x] Rate limit ativo nos endpoints listados (cupom quando existir)
- [x] Nenhum segredo no repositório (gitleaks no CI; exceção só para as chaves de dev do `application-local.yml`)
- [ ] Dependências sem vulnerabilidade crítica (Dependabot configurado; falta rodar `pnpm audit` e OWASP dependency-check antes do piloto)
- [ ] Headers de segurança conferidos (configurados; falta tirar a CSP completa do `Report-Only` depois de ver no navegador)
- [ ] CPF criptografado e mascarado ponta a ponta
- [ ] Backup restaurado com sucesso
