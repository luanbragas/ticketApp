import { describe, expect, it } from "vitest"

import type { PublicAvailability, TicketBatch } from "@/lib/api/types"

import { batchDetail, batchStatusLabel, lowestPrice } from "./ticket-format"

const batch: TicketBatch = {
  id: "b1",
  name: "Lote 1",
  priceCents: 3000,
  capacity: 100,
  sold: 40,
  reserved: 2,
  remaining: 58,
  salesStartAt: null,
  salesEndAt: "2026-11-11T02:00:00Z",
  maxPerOrder: null,
  visible: true,
  status: "ON_SALE",
}

describe("ticket-format", () => {
  it("diz quando o lote na fila abre", () => {
    const now = new Date("2026-11-01T12:00:00Z")
    expect(
      batchStatusLabel(
        { status: "SCHEDULED", salesStartAt: "2026-11-11T02:00:00Z" },
        now,
      ),
    ).toBe("Abre Ter 10.11 23h")
    expect(
      batchStatusLabel({ status: "SCHEDULED", salesStartAt: null }, now),
    ).toBe("Na fila")
    expect(
      batchStatusLabel({ status: "SOLD_OUT", salesStartAt: null }, now),
    ).toBe("Esgotado")
  })

  it("resume preço, vendas e virada", () => {
    expect(batchDetail(batch)).toBe(
      "R$ 30,00 · 40 de 100 vendidos · vira Ter 10.11 23h",
    )
    expect(batchDetail({ ...batch, status: "SCHEDULED", sold: 0 })).toBe(
      "R$ 30,00 · 100 ingressos · vira Ter 10.11 23h",
    )
    expect(batchDetail({ ...batch, status: "CLOSED" })).toBe(
      "R$ 30,00 · 40 de 100 vendidos",
    )
  })

  it("acha o menor preço à venda", () => {
    const b = (
      priceCents: number,
      status: TicketBatch["status"],
      availability = "AVAILABLE",
    ) => ({
      id: String(priceCents),
      name: "Lote",
      priceCents,
      status,
      availability: availability as "AVAILABLE",
      salesStartAt: null,
      salesEndAt: null,
      maxPerOrder: 10,
    })
    const availability: PublicAvailability = {
      eventStatus: "PUBLISHED",
      types: [
        {
          name: "Pista",
          description: null,
          halfPrice: false,
          batches: [b(2000, "SOLD_OUT"), b(4000, "ON_SALE")],
        },
        {
          name: "Meia",
          description: null,
          halfPrice: true,
          batches: [b(1500, "ON_SALE", "UNAVAILABLE"), b(2500, "ON_SALE")],
        },
      ],
    }
    expect(lowestPrice(availability)).toBe(2500)
    expect(lowestPrice({ eventStatus: "PUBLISHED", types: [] })).toBeNull()
  })
})
