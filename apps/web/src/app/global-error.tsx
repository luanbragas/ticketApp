"use client"

import * as Sentry from "@sentry/nextjs"
import { useEffect } from "react"

/** Último recurso: erro no layout raiz. Manda para o Sentry e oferece recarregar. */
export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string }
  reset: () => void
}) {
  useEffect(() => {
    Sentry.captureException(error)
  }, [error])

  return (
    <html lang="pt-BR">
      <body
        style={{
          margin: 0,
          minHeight: "100dvh",
          display: "grid",
          placeItems: "center",
          background: "#0a0a0a",
          color: "#fafafa",
          fontFamily: "system-ui, sans-serif",
          padding: 16,
        }}
      >
        <main style={{ maxWidth: 420, display: "grid", gap: 12 }}>
          <h1 style={{ margin: 0, fontSize: 32 }}>Deu ruim aqui.</h1>
          <p style={{ margin: 0, opacity: 0.8 }}>
            Algo quebrou do nosso lado. Já fomos avisados. Tente de novo em
            instantes.
          </p>
          <button
            type="button"
            onClick={reset}
            style={{
              height: 48,
              border: 0,
              background: "#d6ff1f",
              color: "#0a0a0a",
              fontWeight: 800,
              cursor: "pointer",
            }}
          >
            Tentar de novo
          </button>
        </main>
      </body>
    </html>
  )
}
