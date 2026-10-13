import { api, API_URL, toApiError } from "./client"
import type { HalfPriceReason, PublicOrder, Quote } from "./types"

export const quote = (
  slug: string,
  items: { batchId: string; quantity: number }[],
) =>
  api<Quote>(`/api/v1/public/events/${encodeURIComponent(slug)}/quote`, {
    method: "POST",
    body: { items },
  })

export type PlaceOrderBody = {
  event: string
  buyer: { name: string; email: string; phone: string; cpf: string }
  tickets: {
    batchId: string
    holderName: string
    holderCpf: string
    halfPriceReason: HalfPriceReason | null
  }[]
  adultDeclared: boolean
  termsAccepted: boolean
}

/**
 * Cria o pedido. A chave vai como Idempotency-Key: clicar de novo (ou a rede repetir) devolve o mesmo
 * pedido, e só quem tem a chave acompanha o status depois (ADR-007).
 */
export async function placeOrder(
  key: string,
  body: PlaceOrderBody,
): Promise<PublicOrder> {
  return api<PublicOrder>("/api/v1/public/orders", {
    method: "POST",
    body,
    headers: { "Idempotency-Key": key },
  })
}

export async function getOrder(id: string, key: string): Promise<PublicOrder> {
  let response: Response
  try {
    response = await fetch(`${API_URL}/api/v1/public/orders/${id}`, {
      headers: { "X-Order-Key": key },
      credentials: "include",
      cache: "no-store",
    })
  } catch {
    throw toApiError(0, {
      type: "network",
      title: "Sem conexão",
      detail: "Não foi possível falar com o servidor.",
    })
  }
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw toApiError(response.status, payload)
  return payload as PublicOrder
}
