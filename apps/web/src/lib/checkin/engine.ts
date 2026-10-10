/**
 * Decisão da portaria no aparelho (ADR-010): o QR lido vira SHA-256 e é conferido na lista baixada, sem
 * internet. Cada entrada aceita vai para a fila de sincronização; o servidor decide conflitos depois
 * (vale a leitura mais antiga).
 */
import type { CheckinManifest, TicketStatus } from "@/lib/api/types"
import type { SyncItem } from "@/lib/api/checkin"

export type GateEntry = CheckinManifest["tickets"][number] & {
  /** Hora em que entrou, lida neste aparelho (ISO). */
  enteredAt?: string
}

export type Gate = {
  byHash: Map<string, GateEntry>
  byId: Map<string, GateEntry>
}

export type Verdict =
  | { kind: "ADMITTED"; entry: GateEntry; at: string }
  | { kind: "ALREADY_IN"; entry: GateEntry; at: string | null }
  | { kind: "INVALID"; entry: GateEntry | null }

export function buildGate(entries: GateEntry[]): Gate {
  const byHash = new Map<string, GateEntry>()
  const byId = new Map<string, GateEntry>()
  for (const entry of entries) {
    const copy = { ...entry }
    byHash.set(copy.tokenHash, copy)
    byId.set(copy.ticketId, copy)
  }
  return { byHash, byId }
}

export function entries(gate: Gate): GateEntry[] {
  return [...gate.byId.values()]
}

const BLOCKED: TicketStatus[] = ["TRANSFERRED", "CANCELLED"]

/**
 * Decide a entrada e, se entrou, marca o ingresso na lista local. Devolve o item a sincronizar quando
 * houve entrada.
 */
export function admit(
  gate: Gate,
  key: { tokenHash: string } | { ticketId: string },
  now: Date,
): { verdict: Verdict; queued: SyncItem | null } {
  const entry =
    "tokenHash" in key
      ? gate.byHash.get(key.tokenHash)
      : gate.byId.get(key.ticketId)
  if (!entry || BLOCKED.includes(entry.status)) {
    return { verdict: { kind: "INVALID", entry: entry ?? null }, queued: null }
  }
  if (entry.status === "CHECKED_IN") {
    return {
      verdict: { kind: "ALREADY_IN", entry, at: entry.enteredAt ?? null },
      queued: null,
    }
  }
  const at = now.toISOString()
  entry.status = "CHECKED_IN"
  entry.enteredAt = at
  const queued: SyncItem =
    "tokenHash" in key
      ? { tokenHash: entry.tokenHash, checkedInAt: at }
      : { ticketId: entry.ticketId, checkedInAt: at }
  return { verdict: { kind: "ADMITTED", entry, at }, queued }
}

/**
 * Lista nova do servidor, sem perder o que este aparelho leu e ainda não sincronizou: quem está na fila
 * continua com check-in local.
 */
export function refresh(
  current: Gate,
  fresh: CheckinManifest["tickets"],
  pending: SyncItem[],
): Gate {
  const pendingHashes = new Set(pending.map((p) => p.tokenHash).filter(Boolean))
  const pendingIds = new Set(pending.map((p) => p.ticketId).filter(Boolean))
  return buildGate(
    fresh.map((ticket) => {
      const local = current.byId.get(ticket.ticketId)
      const waiting =
        pendingHashes.has(ticket.tokenHash) || pendingIds.has(ticket.ticketId)
      if (waiting && local?.status === "CHECKED_IN") {
        return { ...ticket, status: "CHECKED_IN", enteredAt: local.enteredAt }
      }
      return {
        ...ticket,
        enteredAt:
          ticket.status === "CHECKED_IN" ? local?.enteredAt : undefined,
      }
    }),
  )
}

/** Sem acento e minúsculo: "José" acha "jose". */
function normalize(text: string): string {
  return text.normalize("NFD").replace(/\p{M}/gu, "").toLowerCase()
}

/** Busca manual por nome na lista local (funciona offline). */
export function searchByName(
  gate: Gate,
  query: string,
  limit = 20,
): GateEntry[] {
  const q = normalize(query.trim())
  if (q.length < 2) return []
  return entries(gate)
    .filter((entry) => normalize(entry.holderName).includes(q))
    .slice(0, limit)
}

export function counts(gate: Gate): { inside: number; total: number } {
  let inside = 0
  let total = 0
  for (const entry of gate.byId.values()) {
    if (BLOCKED.includes(entry.status)) continue
    total++
    if (entry.status === "CHECKED_IN") inside++
  }
  return { inside, total }
}

/** SHA-256 em hexadecimal, igual ao token_hash do servidor. */
export async function sha256Hex(text: string): Promise<string> {
  const digest = await crypto.subtle.digest(
    "SHA-256",
    new TextEncoder().encode(text),
  )
  return [...new Uint8Array(digest)]
    .map((b) => b.toString(16).padStart(2, "0"))
    .join("")
}

/** QR do FESTA: o token puro ou o link /t/{token}. */
export function tokenFromQr(raw: string): string | null {
  const text = raw.trim()
  const match = /(?:\/t\/)?([A-Za-z0-9_-]{43})$/.exec(text)
  return match ? match[1] : null
}
