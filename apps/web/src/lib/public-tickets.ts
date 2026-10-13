import "server-only"

import type { PublicTicket } from "@/lib/api/types"

const API_URL =
  process.env.API_INTERNAL_URL ??
  process.env.NEXT_PUBLIC_API_URL ??
  "http://localhost:8080"

const TOKEN = /^[A-Za-z0-9_-]{43}$/

/** Ingresso pelo token do QR. Sem cache: o status muda na entrada. Nulo se não existe. */
export async function getPublicTicket(
  token: string,
): Promise<PublicTicket | null> {
  if (!TOKEN.test(token)) return null
  const response = await fetch(`${API_URL}/api/v1/public/tickets/${token}`, {
    cache: "no-store",
  })
  if (response.status === 404) return null
  if (!response.ok) {
    throw new Error(`API /public/tickets respondeu ${response.status}`)
  }
  return (await response.json()) as PublicTicket
}
