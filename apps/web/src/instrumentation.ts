import * as Sentry from "@sentry/nextjs"

/**
 * Erros do servidor do Next no Sentry (PLAN.md M0). Sem SENTRY_DSN não inicia: dev e testes ficam de fora.
 * LGPD: sem IP, cookies ou corpo de requisição nos eventos.
 */
export function register() {
  const dsn = process.env.SENTRY_DSN
  if (!dsn) return
  Sentry.init({
    dsn,
    environment: process.env.SENTRY_ENVIRONMENT ?? "local",
    sendDefaultPii: false,
    tracesSampleRate: 0,
  })
}

export const onRequestError = Sentry.captureRequestError
