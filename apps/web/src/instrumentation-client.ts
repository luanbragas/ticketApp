import * as Sentry from "@sentry/nextjs"

// Erros do navegador no Sentry (PLAN.md M0). Sem NEXT_PUBLIC_SENTRY_DSN não inicia. Sem replay de sessão:
// a tela de checkout tem CPF e dados pessoais (SECURITY.md §LGPD).
const dsn = process.env.NEXT_PUBLIC_SENTRY_DSN
if (dsn) {
  Sentry.init({
    dsn,
    environment: process.env.NEXT_PUBLIC_SENTRY_ENVIRONMENT ?? "local",
    sendDefaultPii: false,
    tracesSampleRate: 0,
  })
}
