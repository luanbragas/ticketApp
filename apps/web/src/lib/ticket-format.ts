import {
  BATCH_STATUS_LABELS,
  type BatchStatus,
  type PublicAvailability,
  type TicketBatch,
} from "@/lib/api/types"

import { dayAndHour } from "./event-format"
import { formatCents } from "./money"

type BatchLike = Pick<TicketBatch, "status" | "salesStartAt">

/** Lote na fila com data de abertura no futuro mostra quando abre; o resto, o status. */
export function batchStatusLabel(batch: BatchLike, now = new Date()): string {
  if (
    batch.status === "SCHEDULED" &&
    batch.salesStartAt &&
    new Date(batch.salesStartAt) > now
  ) {
    return `Abre ${dayAndHour(batch.salesStartAt)}`
  }
  return BATCH_STATUS_LABELS[batch.status]
}

/** "R$ 30,00 · 40 de 100 vendidos · vira Ter 10.11 23h". */
export function batchDetail(batch: TicketBatch): string {
  const parts = [formatCents(batch.priceCents)]
  parts.push(
    batch.status === "SCHEDULED"
      ? `${batch.capacity} ingressos`
      : `${batch.sold} de ${batch.capacity} vendidos`,
  )
  if (batch.salesEndAt && !isFinal(batch.status)) {
    parts.push(`vira ${dayAndHour(batch.salesEndAt)}`)
  }
  return parts.join(" · ")
}

export function isFinal(status: BatchStatus): boolean {
  return status === "SOLD_OUT" || status === "CLOSED"
}

/** Menor preço entre os lotes à venda com ingresso, para o "a partir de" da página. */
export function lowestPrice(availability: PublicAvailability): number | null {
  const prices = availability.types.flatMap((t) =>
    t.batches
      .filter((b) => b.status === "ON_SALE" && b.availability !== "UNAVAILABLE")
      .map((b) => b.priceCents),
  )
  return prices.length ? Math.min(...prices) : null
}
