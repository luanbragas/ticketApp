import { api } from "./client"
import type {
  CheckinManifest,
  Dashboard,
  Participants,
  SyncResponse,
  TicketStatus,
} from "./types"

const event = (orgId: string, eventId: string) =>
  `/api/v1/orgs/${orgId}/events/${eventId}`

export const getDashboard = (orgId: string, eventId: string) =>
  api<Dashboard>(`${event(orgId, eventId)}/dashboard`)

export const getParticipants = (
  orgId: string,
  eventId: string,
  params: { q?: string; status?: TicketStatus; limit?: number },
) => {
  const search = new URLSearchParams()
  if (params.q) search.set("q", params.q)
  if (params.status) search.set("status", params.status)
  if (params.limit) search.set("limit", String(params.limit))
  const query = search.toString()
  return api<Participants>(
    `${event(orgId, eventId)}/attendees${query ? `?${query}` : ""}`,
  )
}

export const undoCheckin = (orgId: string, checkinId: string) =>
  api<void>(`/api/v1/orgs/${orgId}/checkins/${checkinId}/undo`, {
    method: "POST",
  })

export const getManifest = (orgId: string, eventId: string) =>
  api<CheckinManifest>(`${event(orgId, eventId)}/checkin/manifest`)

export type SyncItem = {
  tokenHash?: string
  ticketId?: string
  checkedInAt: string
}

export const syncCheckins = (
  orgId: string,
  eventId: string,
  deviceId: string,
  items: SyncItem[],
) =>
  api<SyncResponse>(`${event(orgId, eventId)}/checkins/sync`, {
    method: "POST",
    body: { deviceId, items },
  })
