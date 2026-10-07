import { api } from "./client"
import type {
  Invitation,
  InvitationPreview,
  Organization,
  Role,
  Team,
} from "./types"

export const createOrganization = (body: { name: string; slug?: string }) =>
  api<Organization>("/api/v1/orgs", { method: "POST", body })

export const getTeam = (orgId: string) =>
  api<Team>(`/api/v1/orgs/${orgId}/members`)

export const inviteMember = (
  orgId: string,
  body: { email: string; role: Role },
) => api<Invitation>(`/api/v1/orgs/${orgId}/members`, { method: "POST", body })

export const previewInvitation = (token: string) =>
  api<InvitationPreview>("/api/v1/public/invitations/preview", {
    method: "POST",
    body: { token },
  })

export const acceptInvitation = (token: string) =>
  api<Organization>("/api/v1/invitations/accept", {
    method: "POST",
    body: { token },
  })
