import { describe, expect, it } from "vitest"

import {
  admit,
  buildGate,
  counts,
  refresh,
  searchByName,
  sha256Hex,
  tokenFromQr,
  type GateEntry,
} from "./engine"

const entry = (
  n: number,
  status: GateEntry["status"] = "VALID",
): GateEntry => ({
  ticketId: `t${n}`,
  tokenHash: `h${n}`,
  holderName: n === 1 ? "José Álvares" : `Pessoa ${n}`,
  typeName: "Pista",
  halfPrice: false,
  status,
})

const NOW = new Date("2026-12-13T03:00:00Z")

describe("checkin engine", () => {
  it("deixa entrar uma vez e marca a hora", () => {
    const gate = buildGate([entry(1), entry(2)])

    const first = admit(gate, { tokenHash: "h1" }, NOW)
    expect(first.verdict.kind).toBe("ADMITTED")
    expect(first.queued).toEqual({
      tokenHash: "h1",
      checkedInAt: NOW.toISOString(),
    })

    const again = admit(gate, { tokenHash: "h1" }, new Date())
    expect(again.verdict).toMatchObject({
      kind: "ALREADY_IN",
      at: NOW.toISOString(),
    })
    expect(again.queued).toBeNull()
    expect(counts(gate)).toEqual({ inside: 1, total: 2 })
  })

  it("recusa QR desconhecido, cancelado ou transferido", () => {
    const gate = buildGate([entry(1, "CANCELLED"), entry(2, "TRANSFERRED")])

    expect(admit(gate, { tokenHash: "nao-existe" }, NOW).verdict.kind).toBe(
      "INVALID",
    )
    expect(admit(gate, { tokenHash: "h1" }, NOW).verdict.kind).toBe("INVALID")
    expect(admit(gate, { ticketId: "t2" }, NOW).verdict.kind).toBe("INVALID")
    expect(counts(gate)).toEqual({ inside: 0, total: 0 })
  })

  it("busca manual entra pelo id do ingresso", () => {
    const gate = buildGate([entry(1)])

    expect(admit(gate, { ticketId: "t1" }, NOW).queued).toEqual({
      ticketId: "t1",
      checkedInAt: NOW.toISOString(),
    })
  })

  it("lista nova do servidor não apaga o que ainda está na fila", () => {
    const gate = buildGate([entry(1), entry(2)])
    const { queued } = admit(gate, { tokenHash: "h1" }, NOW)

    const fresh = refresh(
      gate,
      [entry(1), entry(2, "CHECKED_IN"), entry(3)],
      [queued!],
    )

    expect(fresh.byId.get("t1")).toMatchObject({
      status: "CHECKED_IN",
      enteredAt: NOW.toISOString(),
    })
    expect(fresh.byId.get("t2")?.status).toBe("CHECKED_IN")
    expect(counts(fresh)).toEqual({ inside: 2, total: 3 })
  })

  it("acha por nome sem acento", () => {
    const gate = buildGate([entry(1), entry(2)])

    expect(searchByName(gate, "jose alv").map((e) => e.ticketId)).toEqual([
      "t1",
    ])
    expect(searchByName(gate, "a")).toEqual([])
  })

  it("lê o token do QR puro ou do link e calcula o hash como o servidor", async () => {
    const token = "u1I9XRZg2t59FSnfbbbIluwighmRBuyeiYB-vx2OCV4"
    expect(tokenFromQr(token)).toBe(token)
    expect(tokenFromQr(`https://festa.com/t/${token}`)).toBe(token)
    expect(tokenFromQr("qualquer coisa")).toBeNull()
    expect(await sha256Hex("abc")).toBe(
      "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
    )
  })
})
