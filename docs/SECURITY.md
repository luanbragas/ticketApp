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

- Token aleatório de 32 bytes, codificado em base64url, assinado com HMAC-SHA256 (chave em env).
- Banco guarda só `token_hash`. QR não contém nome, CPF nem ID sequencial.
- Ingresso cancelado ou transferido invalida o token na hora.

## Abuso e disponibilidade

- Rate limit (Bucket4j em memória no MVP) em: login, link mágico, criação de pedido, validação de cupom, webhook.
- Cloudflare Turnstile no checkout em aberturas de lote.
- Upload: URL pré-assinada, só `image/jpeg|png|webp`, máximo 5 MB, nome gerado pelo servidor.

## Entrada e saída

- Validação com Bean Validation (api) e Zod (web). Rejeitar campos desconhecidos.
- Só consultas parametrizadas. Nada de concatenar SQL.
- CORS restrito aos domínios da web.
- Headers: CSP, HSTS, X-Content-Type-Options, Referrer-Policy.
- Erros em Problem Details sem stack trace nem detalhes internos.

## Dados pessoais

- CPF criptografado (AES-GCM, chave em env/KMS) + `cpf_hash` (HMAC) para busca. Exibir mascarado: `***.456.789-**`.
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
- [ ] Rate limit ativo nos endpoints listados
- [ ] Nenhum segredo no repositório (rodar gitleaks no CI)
- [ ] Dependências sem vulnerabilidade crítica (Dependabot / `pnpm audit` / OWASP dependency-check)
- [ ] Headers de segurança conferidos
- [ ] CPF criptografado e mascarado ponta a ponta
- [ ] Backup restaurado com sucesso
