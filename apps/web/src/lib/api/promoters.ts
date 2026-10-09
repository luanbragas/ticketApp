import { api } from "./client"
import type { EventPromoters, Promoter } from "./types"

const base = (orgId: string, eventId: string) =>
  `/api/v1/orgs/${orgId}/events/${eventId}/promoters`

export const getEventPromoters = (orgId: string, eventId: string) =>
  api<EventPromoters>(base(orgId, eventId))

export const listPromoters = (orgId: string) =>
  api<Promoter[]>(`/api/v1/orgs/${orgId}/promoters`)

/** Promoter já cadastrado (promoterId) ou novo (nome, telefone, membro da equipe). */
export const addEventPromoter = (
  orgId: string,
  eventId: string,
  body: {
    promoterId?: string
    name?: string
    phone?: string
    userId?: string
    code?: string
  },
) => api<EventPromoters>(base(orgId, eventId), { method: "POST", body })

export const setPromoterLinkActive = (
  orgId: string,
  eventId: string,
  linkId: string,
  active: boolean,
) =>
  api<EventPromoters>(`${base(orgId, eventId)}/${linkId}`, {
    method: "PATCH",
    body: { active },
  })
