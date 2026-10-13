import { z } from "zod"

import type { BatchBody } from "@/lib/api/tickets"
import type { TicketBatch } from "@/lib/api/types"
import { centsToInput, parseReais } from "@/lib/money"

import { toInstant, toLocalParts } from "./event"

/** Campo datetime-local ("2026-11-10T23:00", parede de São Paulo) ↔ instante da API. */
export function localToInstant(value: string): string | null {
  if (!value) return null
  const [date, time] = value.split("T")
  return toInstant(date, time)
}

export function instantToLocal(iso: string | null): string {
  if (!iso) return ""
  const { date, time } = toLocalParts(iso)
  return `${date}T${time}`
}

const MAX_PRICE_CENTS = 100_000_000

export const batchSchema = z
  .object({
    name: z
      .string()
      .trim()
      .min(1, "Informe o nome do lote.")
      .max(60, "Nome muito longo."),
    price: z.string().refine((v) => {
      const cents = parseReais(v)
      return cents !== null && cents >= 1 && cents <= MAX_PRICE_CENTS
    }, "Use um preço como 30 ou 30,50."),
    capacity: z
      .string()
      .regex(/^\d+$/, "Informe quantos ingressos.")
      .refine(
        (v) => Number(v) >= 1 && Number(v) <= 100_000,
        "Use de 1 a 100.000.",
      ),
    opensAt: z.string(),
    turnsAt: z.string(),
    maxPerOrder: z
      .string()
      .refine(
        (v) =>
          v === "" || (/^\d+$/.test(v) && Number(v) >= 1 && Number(v) <= 20),
        "Use de 1 a 20.",
      ),
    visible: z.boolean(),
  })
  .refine((v) => !v.opensAt || !v.turnsAt || v.turnsAt > v.opensAt, {
    path: ["turnsAt"],
    message: "A virada precisa ser depois da abertura.",
  })

export type BatchInput = z.infer<typeof batchSchema>

export function emptyBatch(position: number): BatchInput {
  return {
    name: `Lote ${position + 1}`,
    price: "",
    capacity: "",
    opensAt: "",
    turnsAt: "",
    maxPerOrder: "",
    visible: true,
  }
}

export function fromBatch(batch: TicketBatch): BatchInput {
  return {
    name: batch.name,
    price: centsToInput(batch.priceCents),
    capacity: String(batch.capacity),
    opensAt: instantToLocal(batch.salesStartAt),
    turnsAt: instantToLocal(batch.salesEndAt),
    maxPerOrder: batch.maxPerOrder ? String(batch.maxPerOrder) : "",
    visible: batch.visible,
  }
}

/** Só chamar com entrada já validada pelo schema. */
export function toBatchBody(input: BatchInput): BatchBody {
  return {
    name: input.name.trim(),
    priceCents: parseReais(input.price)!,
    capacity: Number(input.capacity),
    salesStartAt: localToInstant(input.opensAt),
    salesEndAt: localToInstant(input.turnsAt),
    maxPerOrder: input.maxPerOrder ? Number(input.maxPerOrder) : null,
    visible: input.visible,
  }
}
