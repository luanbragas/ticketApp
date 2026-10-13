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

/** Evento como o público vê (GET /public/events/{slug}). Sem ids internos. */
export type PublicEvent = {
  slug: string
  name: string
  description: string | null
  startsAt: string
  endsAt: string
  venueName: string
  address: string | null
  city: string | null
  minAge: number
  hasOpenBar: boolean
  accentColor: string | null
  status: "PUBLISHED" | "ENDED"
  flyer: Flyer | null
  lineup: { name: string; startsAt: string | null }[]
  organizer: {
    name: string
    slug: string
    logoUrl: string | null
    instagram: string | null
  }
}

/** Lote: só anda para a frente (ADR-006). */
export type BatchStatus = "SCHEDULED" | "ON_SALE" | "SOLD_OUT" | "CLOSED"

export const BATCH_STATUS_LABELS: Record<BatchStatus, string> = {
  SCHEDULED: "Na fila",
  ON_SALE: "À venda",
  SOLD_OUT: "Esgotado",
  CLOSED: "Encerrado",
}

export type TicketBatch = {
  id: string
  name: string
  priceCents: number
  capacity: number
  sold: number
  reserved: number
  remaining: number
  /** Não abre antes de (ISO-8601, UTC). */
  salesStartAt: string | null
  /** Vira (fecha) em (ISO-8601, UTC). */
  salesEndAt: string | null
  maxPerOrder: number | null
  visible: boolean
  status: BatchStatus
}

export type TicketType = {
  id: string
  name: string
  description: string | null
  halfPrice: boolean
  batches: TicketBatch[]
}

/** Cota de meia: mínimo legal de ingressos de meia sobre o total oferecido. */
export type HalfPriceQuota = {
  percent: number
  total: number
  halfPrice: number
  minimum: number
  met: boolean
}

/** GET /orgs/{org}/events/{id}/ticket-types; toda escrita devolve o mesmo formato. */
export type TicketCatalog = {
  types: TicketType[]
  halfPriceQuota: HalfPriceQuota
}

/** GET /public/events/{slug}/availability. Sem números de venda. */
export type PublicAvailability = {
  eventStatus: EventStatus
  types: {
    name: string
    description: string | null
    halfPrice: boolean
    batches: {
      id: string
      name: string
      priceCents: number
      status: BatchStatus
      availability: "AVAILABLE" | "LAST_UNITS" | "UNAVAILABLE"
      salesStartAt: string | null
      salesEndAt: string | null
      maxPerOrder: number
    }[]
  }[]
}

/** POST /public/events/{slug}/quote: valores calculados no backend (centavos). */
export type Quote = {
  lines: {
    batchId: string
    batchName: string
    typeName: string
    halfPrice: boolean
    quantity: number
    unitPriceCents: number
    unitFeeCents: number
    totalCents: number
    maxPerOrder: number
  }[]
  subtotalCents: number
  feeCents: number
  totalCents: number
}

export type HalfPriceReason = "STUDENT" | "PCD" | "YOUTH_LOW_INCOME" | "SENIOR"

export const HALF_PRICE_REASONS: Record<HalfPriceReason, string> = {
  STUDENT: "Estudante (carteirinha CIE)",
  PCD: "Pessoa com deficiência",
  YOUTH_LOW_INCOME: "Jovem de baixa renda (ID Jovem)",
  SENIOR: "Pessoa com 60 anos ou mais",
}

export type OrderStatus =
  | "PENDING_PAYMENT"
  | "PAID"
  | "EXPIRED"
  | "FAILED"
  | "REFUNDED"
  | "PARTIALLY_REFUNDED"
  | "CHARGEBACK"

/** Pedido como o comprador vê (CPF sempre mascarado). */
export type PublicOrder = {
  id: string
  status: OrderStatus
  expiresAt: string
  event: { slug: string; name: string; startsAt: string; minAge: number }
  buyer: { name: string; email: string; cpf: string }
  items: {
    batchName: string
    typeName: string
    unitPriceCents: number
    feeCents: number
    halfPrice: boolean
    halfPriceReason: HalfPriceReason | null
    holderName: string
    holderCpf: string
  }[]
  subtotalCents: number
  feeCents: number
  totalCents: number
}
