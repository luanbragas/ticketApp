import { describe, expect, it } from "vitest"

import type { TicketBatch } from "@/lib/api/types"

import {
  batchSchema,
  emptyBatch,
  fromBatch,
  instantToLocal,
  localToInstant,
  toBatchBody,
} from "./batch"

describe("batch form", () => {
  it("converte a data da tela (São Paulo) para instante e volta", () => {
    expect(localToInstant("2026-11-10T23:00")).toBe("2026-11-11T02:00:00.000Z")
    expect(localToInstant("")).toBeNull()
    expect(instantToLocal("2026-11-11T02:00:00.000Z")).toBe("2026-11-10T23:00")
  })

  it("monta o corpo da API em centavos", () => {
    const input = {
      ...emptyBatch(1),
      price: "45,90",
      capacity: "150",
      turnsAt: "2026-11-10T23:00",
    }
    expect(batchSchema.safeParse(input).success).toBe(true)
    expect(toBatchBody(input)).toEqual({
      name: "Lote 2",
      priceCents: 4590,
      capacity: 150,
      salesStartAt: null,
      salesEndAt: "2026-11-11T02:00:00.000Z",
      maxPerOrder: null,
      visible: true,
    })
  })

  it("recusa virada antes da abertura e preço zerado", () => {
    const result = batchSchema.safeParse({
      ...emptyBatch(0),
      price: "0",
      capacity: "100",
      opensAt: "2026-11-10T23:00",
      turnsAt: "2026-11-10T22:00",
    })
    expect(result.success).toBe(false)
    const paths = result.error!.issues.map((i) => i.path[0])
    expect(paths).toEqual(expect.arrayContaining(["price", "turnsAt"]))
  })

  it("preenche o formulário com o lote salvo", () => {
    const batch: TicketBatch = {
      id: "b1",
      name: "Lote 1",
      priceCents: 3000,
      capacity: 100,
      sold: 0,
      reserved: 0,
      remaining: 100,
      salesStartAt: null,
      salesEndAt: "2026-11-11T02:00:00Z",
      maxPerOrder: 4,
      visible: false,
      status: "ON_SALE",
    }
    expect(fromBatch(batch)).toEqual({
      name: "Lote 1",
      price: "30,00",
      capacity: "100",
      opensAt: "",
      turnsAt: "2026-11-10T23:00",
      maxPerOrder: "4",
      visible: false,
    })
  })
})
