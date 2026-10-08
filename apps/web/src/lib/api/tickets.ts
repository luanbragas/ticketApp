import { api } from "./client"
import type { TicketCatalog } from "./types"

const base = (orgId: string, eventId: string) =>
  `/api/v1/orgs/${orgId}/events/${eventId}`

/** Lote inteiro: na edição troca tudo (PUT); data nula = sem data. */
export type BatchBody = {
  name: string
  priceCents: number
  capacity: number
  salesStartAt: string | null
  salesEndAt: string | null
  maxPerOrder: number | null
  visible: boolean
}

export const getCatalog = (orgId: string, eventId: string) =>
  api<TicketCatalog>(`${base(orgId, eventId)}/ticket-types`)

export const createTicketType = (
  orgId: string,
  eventId: string,
  body: { name: string; description?: string; halfPrice: boolean },
) =>
  api<TicketCatalog>(`${base(orgId, eventId)}/ticket-types`, {
    method: "POST",
    body,
  })

export const updateTicketType = (
  orgId: string,
  eventId: string,
  typeId: string,
  body: { name?: string; description?: string },
) =>
  api<TicketCatalog>(`${base(orgId, eventId)}/ticket-types/${typeId}`, {
    method: "PATCH",
    body,
  })

export const deleteTicketType = (
  orgId: string,
  eventId: string,
  typeId: string,
) =>
  api<TicketCatalog>(`${base(orgId, eventId)}/ticket-types/${typeId}`, {
    method: "DELETE",
  })

export const createBatch = (
  orgId: string,
  eventId: string,
  typeId: string,
  body: BatchBody,
) =>
  api<TicketCatalog>(`${base(orgId, eventId)}/ticket-types/${typeId}/batches`, {
    method: "POST",
    body,
  })

export const updateBatch = (
  orgId: string,
  eventId: string,
  batchId: string,
  body: BatchBody,
) =>
  api<TicketCatalog>(`${base(orgId, eventId)}/batches/${batchId}`, {
    method: "PUT",
    body,
  })

export const closeBatch = (orgId: string, eventId: string, batchId: string) =>
  api<TicketCatalog>(`${base(orgId, eventId)}/batches/${batchId}/close`, {
    method: "POST",
  })

export const deleteBatch = (orgId: string, eventId: string, batchId: string) =>
  api<TicketCatalog>(`${base(orgId, eventId)}/batches/${batchId}`, {
    method: "DELETE",
  })
