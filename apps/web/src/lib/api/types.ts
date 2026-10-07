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
