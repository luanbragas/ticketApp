import { api, ApiError, API_URL, toApiError } from "./client"
import type { OrderPayment, PaymentAccountStatus } from "./types"

const orderPath = (orderId: string) =>
  `/api/v1/public/orders/${orderId}/payment`

/** Gera o PIX (ou devolve o que já existe e ainda vale). */
export const payWithPix = (orderId: string, key: string) =>
  api<OrderPayment>(orderPath(orderId), {
    method: "POST",
    body: { method: "PIX" },
    headers: { "X-Order-Key": key },
  })

/** Cartão já tokenizado pelo SDK do gateway (ou token de teste do simulado). */
export const payWithCard = (
  orderId: string,
  key: string,
  card: { cardToken: string; paymentMethodId: string; issuerId?: string },
) =>
  api<OrderPayment>(orderPath(orderId), {
    method: "POST",
    body: { method: "CARD", ...card },
    headers: { "X-Order-Key": key },
  })

/** Último pagamento do pedido; nulo se ainda não começou. */
export async function getOrderPayment(
  orderId: string,
  key: string,
): Promise<OrderPayment | null> {
  let response: Response
  try {
    response = await fetch(`${API_URL}${orderPath(orderId)}`, {
      headers: { "X-Order-Key": key },
      credentials: "include",
      cache: "no-store",
    })
  } catch {
    throw new ApiError(
      0,
      "network",
      "Sem conexão",
      "Sem conexão com o servidor.",
    )
  }
  if (response.status === 204) return null
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw toApiError(response.status, payload)
  return payload as OrderPayment
}

/** Só existe com o gateway simulado: "o comprador pagou o PIX". */
export const simulateApproval = (orderId: string, key: string) =>
  api<void>(`${orderPath(orderId)}/simulate-approval`, {
    method: "POST",
    headers: { "X-Order-Key": key },
  })

const accountPath = (orgId: string) => `/api/v1/orgs/${orgId}/payment-account`

export const getPaymentAccount = (orgId: string) =>
  api<PaymentAccountStatus>(accountPath(orgId))

export const connectPaymentAccount = (orgId: string) =>
  api<{ authorizationUrl: string }>(`${accountPath(orgId)}/connect`, {
    method: "POST",
  })

export const finishPaymentAccount = (
  orgId: string,
  body: { code: string; state: string },
) =>
  api<PaymentAccountStatus>(`${accountPath(orgId)}/callback`, {
    method: "POST",
    body,
  })

export const disconnectPaymentAccount = (orgId: string) =>
  api<void>(accountPath(orgId), { method: "DELETE" })

/** Reembolso do pedido inteiro (dono e admin): estorna no gateway e cancela os ingressos. */
export const refundOrder = (orgId: string, orderId: string, reason: string) =>
  api<{ orderId: string; amountCents: number }>(
    `/api/v1/orgs/${orgId}/orders/${orderId}/refund`,
    { method: "POST", body: { reason } },
  )
