import { api } from "./client"
import type { EventFinance, FinanceOverview } from "./types"

export const getFinanceOverview = (orgId: string) =>
  api<FinanceOverview>(`/api/v1/orgs/${orgId}/finance`)

export const getEventFinance = (orgId: string, eventId: string) =>
  api<EventFinance>(`/api/v1/orgs/${orgId}/events/${eventId}/finance`)
