import { api } from "./client"
import type {
  Invitation,
  InvitationPreview,
  Organization,
  Role,
  Team,
  UploadUrl,
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

/** Campos ausentes ficam como estão; Instagram e WhatsApp vazios apagam. */
export const updateOrganization = (
  orgId: string,
  body: { name?: string; instagram?: string; whatsapp?: string },
) => api<Organization>(`/api/v1/orgs/${orgId}`, { method: "PATCH", body })

export const requestLogoUploadUrl = (
  orgId: string,
  body: { contentType: string; size: number },
) =>
  api<UploadUrl>(`/api/v1/orgs/${orgId}/logo/upload-url`, {
    method: "POST",
    body,
  })

/** key nula tira a logo. */
export const setOrganizationLogo = (orgId: string, key: string | null) =>
  api<Organization>(`/api/v1/orgs/${orgId}/logo`, {
    method: "PUT",
    body: { key },
  })
