import type { FieldValues, Path, UseFormSetError } from "react-hook-form"

import { ApiError } from "@/lib/api/client"

/**
 * Leva os erros de campo da API (Problem Details `errors[]`) para o formulário.
 * Devolve a mensagem geral a exibir quando o erro não for de um campo conhecido.
 */
export function applyApiError<T extends FieldValues>(
  error: unknown,
  setError: UseFormSetError<T>,
  fields: readonly Path<T>[],
): string | null {
  if (!(error instanceof ApiError)) {
    return "Algo deu errado. Tente novamente."
  }
  let matched = false
  for (const fieldError of error.errors) {
    if ((fields as readonly string[]).includes(fieldError.field)) {
      setError(fieldError.field as Path<T>, { message: fieldError.message })
      matched = true
    }
  }
  return matched ? null : error.message
}

const NEXT_KEY = "festa:next"
const NEXT_TTL_MS = 30 * 60 * 1000

/**
 * Guarda para onde voltar depois do link mágico, que abre numa aba nova (vinda do e-mail)
 * sem o ?next= original. Vale 30 minutos neste navegador.
 */
export function rememberNext(next: string | undefined) {
  try {
    if (next)
      localStorage.setItem(NEXT_KEY, JSON.stringify({ next, at: Date.now() }))
    else localStorage.removeItem(NEXT_KEY)
  } catch {
    // Armazenamento indisponível (aba privada): volta para o painel.
  }
}

export function takeRememberedNext(now = Date.now()): string | undefined {
  try {
    const raw = localStorage.getItem(NEXT_KEY)
    localStorage.removeItem(NEXT_KEY)
    if (!raw) return undefined
    const { next, at } = JSON.parse(raw) as { next?: unknown; at?: unknown }
    return typeof next === "string" &&
      typeof at === "number" &&
      now - at < NEXT_TTL_MS
      ? next
      : undefined
  } catch {
    return undefined
  }
}

/** Só aceita redirecionar para caminhos internos (evita open redirect via ?next=). */
export function safeNext(
  next: string | null | undefined,
  fallback = "/painel",
): string {
  if (
    !next ||
    !next.startsWith("/") ||
    next.startsWith("//") ||
    next.startsWith("/\\")
  ) {
    return fallback
  }
  return next
}
