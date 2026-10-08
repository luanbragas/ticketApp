/** Tipos das respostas da API (espelham os records dos controllers). */

export type Role =
  "OWNER" | "ADMIN" | "MANAGER" | "PROMOTER" | "CHECKIN_OPERATOR"

export const ROLE_LABELS: Record<Role, string> = {
  OWNER: "Dono",
  ADMIN: "Administrador",
  MANAGER: "Gerente",
  PROMOTER: "Promoter",
  CHECKIN_OPERATOR: "Operador de check-in",
}

export type Me = {
  id: string
  /** Nulo para conta criada por link mágico (ADR-004). */
  name: string | null
  email: string
  emailVerified: boolean
}

export type Organization = {
  id: string
  name: string
  slug: string
  logoUrl: string | null
  instagram: string | null
  whatsapp: string | null
  role: Role
}

export type Member = {
  userId: string
  name: string | null
  email: string
  role: Role
}

export type Invitation = {
  id: string
  email: string
  role: Role
  expiresAt: string
}

export type Team = {
  members: Member[]
  pendingInvitations: Invitation[]
}

export type InvitationPreview = {
  organizationName: string
  email: string
  role: Role
  roleLabel: string
}

export type EventStatus = "DRAFT" | "PUBLISHED" | "ENDED" | "CANCELLED"

export const EVENT_STATUS_LABELS: Record<EventStatus, string> = {
  DRAFT: "Rascunho",
  PUBLISHED: "Publicado",
  ENDED: "Encerrado",
  CANCELLED: "Cancelado",
}

export type Flyer = { url: string; width: number; height: number }

export type EventDetail = {
  id: string
  slug: string
  name: string
  description: string | null
  category: string | null
  /** ISO-8601 em UTC; exibir em America/Sao_Paulo. */
  startsAt: string | null
  endsAt: string | null
  venueName: string | null
  address: string | null
  city: string | null
  minAge: number
  hasOpenBar: boolean
  halfPriceQuotaPercent: number
  maxTicketsPerCpf: number | null
  /** Cor de destaque da página (#rrggbb), tirada do flyer; nula = verde da plataforma. */
  accentColor: string | null
  status: EventStatus
  publishedAt: string | null
  flyer: Flyer | null
  lineup: { name: string; startsAt: string | null }[]
  pageUrl: string
}

export type EventSummary = {
  id: string
  slug: string
  name: string
  status: EventStatus
  startsAt: string | null
  venueName: string | null
  flyerUrl: string | null
}

export type EventList = { items: EventSummary[] }

export type UploadUrl = {
  uploadUrl: string
  method: "PUT"
  headers: Record<string, string>
  key: string
  publicUrl: string
  expiresAt: string
}
