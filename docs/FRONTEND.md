# FRONTEND.md

## Stack

Next.js (App Router) · React · TypeScript strict · Tailwind · shadcn/ui (Radix) · Lucide · TanStack Query · React Hook Form + Zod · date-fns (fuso `America/Sao_Paulo`) · Vitest + Testing Library · Playwright.

## Estrutura

```
apps/web/src/
├── app/
│   ├── (public)/
│   │   ├── page.tsx                  # landing
│   │   ├── e/[slug]/page.tsx         # página do evento (SSR + OG)
│   │   ├── e/[slug]/checkout/
│   │   ├── pedido/[id]/              # pagamento PIX / status
│   │   ├── t/[token]/page.tsx        # ingresso
│   │   └── meus-ingressos/
│   ├── (auth)/entrar, cadastro, link-magico
│   ├── painel/
│   │   ├── layout.tsx                # shell: menu lateral (desktop) / barra inferior (mobile)
│   │   ├── page.tsx                  # dashboard
│   │   ├── eventos/ (lista, novo = wizard, [id]/ingressos, participantes, promoters)
│   │   ├── financeiro/
│   │   └── configuracoes/
│   └── checkin/[eventId]/            # PWA de check-in
├── components/
│   ├── ui/                           # shadcn (não editar à mão sem motivo)
│   └── <dominio>/                    # event-card, ticket-selector, qr-scanner...
├── lib/
│   ├── api/                          # cliente HTTP + funções por recurso
│   ├── schemas/                      # Zod compartilhado com formulários
│   ├── format.ts                     # moeda (centavos → R$), datas, CPF mascarado
│   └── auth.ts
└── hooks/
```

## Padrões

- **Mobile-first:** projetar em 375 px primeiro. Uma coluna, botão principal fixo no rodapé, áreas de toque ≥ 44 px.
- **Server Components** para páginas públicas (SEO, OG, performance). Client Components só onde há interação.
- **Dados do painel:** TanStack Query no cliente; chaves de query por recurso (`['events', orgId]`).
- **Formulários:** React Hook Form + Zod; mensagens de erro em português; desabilitar submit durante envio.
- **Dinheiro:** a API envia centavos; formatar só na exibição com `Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' })`. Nunca calcular total no front para cobrar.
- **Estados obrigatórios** em toda tela com dados: carregando (skeleton), vazio (com ação), erro (com tentar de novo), sucesso.
- **Erros da API:** Problem Details → mostrar `detail`; erros de campo (`errors[]`) vão para o campo do formulário.
- **Acessibilidade:** labels em todos os inputs, contraste AA, foco visível, navegação por teclado no painel.

## Página do evento

- `generateMetadata` com título, descrição e flyer para Open Graph / WhatsApp.
- Cache na CDN com revalidação curta; disponibilidade dos lotes buscada no cliente (dado que muda rápido).
- Botão "Comprar" fixo no rodapé no mobile.

## Checkout

- Uma coluna; dados do comprador → titulares (se mais de um ingresso) → meia-entrada → 18+ → termos → método.
- Máscaras de CPF e telefone; validar dígitos do CPF no cliente e no servidor.
- Após criar pedido: redirecionar para `/pedido/[id]` (contador de expiração + polling de status a cada 3 s).

## PWA de check-in

- Manifest + service worker; funciona instalado na tela inicial.
- Câmera com `getUserMedia` + leitor de QR (ex.: `@zxing/browser`).
- Lista da portaria e fila de check-ins em **IndexedDB** (ex.: `idb`).
- Indicador claro de online/offline e de itens pendentes de sincronização.
- Resultado ocupa a tela inteira: verde (válido), amarelo (já utilizado + horário + portaria), vermelho (inválido). Vibração e som.

## Testes

- Vitest para utilitários (`format`, schemas) e componentes críticos (seletor de ingressos).
- Playwright para fluxos E2E: comprar com PIX (sandbox), criar evento, check-in.
