import { api } from "./client"
import type { EventDetail, EventList, EventStatus, UploadUrl } from "./types"

const base = (orgId: string) => `/api/v1/orgs/${orgId}/events`

export const listEvents = (orgId: string, status?: EventStatus) =>
  api<EventList>(base(orgId) + (status ? `?status=${status}` : ""))

export const getEvent = (orgId: string, eventId: string) =>
  api<EventDetail>(`${base(orgId)}/${eventId}`)

export const createEvent = (orgId: string, name: string) =>
  api<EventDetail>(base(orgId), { method: "POST", body: { name } })

/** Campos ausentes ficam como estão; texto vazio apaga campo opcional. */
export type EventChanges = Partial<{
  name: string
  description: string
  category: string
  startsAt: string
  endsAt: string
  venueName: string
  address: string
  city: string
  minAge: number
  hasOpenBar: boolean
  halfPriceQuotaPercent: number
  maxTicketsPerCpf: number
  accentColor: string
  lineup: { name: string; startsAt?: string | null }[]
}>

export const updateEvent = (
  orgId: string,
  eventId: string,
  changes: EventChanges,
) =>
  api<EventDetail>(`${base(orgId)}/${eventId}`, {
    method: "PATCH",
    body: changes,
  })

export const requestUploadUrl = (
  orgId: string,
  eventId: string,
  body: { kind: "FLYER"; contentType: string; size: number },
) =>
  api<UploadUrl>(`${base(orgId)}/${eventId}/media/upload-url`, {
    method: "POST",
    body,
  })

export const setFlyer = (
  orgId: string,
  eventId: string,
  body: { key: string; width: number; height: number },
) =>
  api<EventDetail>(`${base(orgId)}/${eventId}/media/flyer`, {
    method: "PUT",
    body,
  })

export const publishEvent = (orgId: string, eventId: string) =>
  api<EventDetail>(`${base(orgId)}/${eventId}/publish`, {
    method: "POST",
    body: {},
  })

/** Envia o arquivo direto ao bucket com a URL pré-assinada (sem cookie da API). */
export async function uploadToStorage(upload: UploadUrl, file: File) {
  const response = await fetch(upload.uploadUrl, {
    method: upload.method,
    headers: upload.headers,
    body: file,
  })
  if (!response.ok) {
    throw new Error(`Envio do arquivo falhou (${response.status})`)
  }
}
