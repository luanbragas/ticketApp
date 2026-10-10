import { describe, expect, it } from "vitest"

import type { EventSummary } from "@/lib/api/types"

import { eventHref, eventLine, filterFrom } from "./event-list"

const base: EventSummary = {
  id: "e1",
  slug: "calourada",
  name: "Calourada",
  status: "DRAFT",
  startsAt: null,
  venueName: null,
  flyerUrl: null,
}

describe("event-list", () => {
  it("lê o filtro da URL e cai em todos quando não conhece", () => {
    expect(filterFrom("rascunhos").status).toBe("DRAFT")
    expect(filterFrom("xpto").value).toBe("todos")
    expect(filterFrom(null).status).toBeUndefined()
  })

  it("monta a linha com data, hora e local", () => {
    expect(eventLine(base)).toBe("Sem data ainda")
    expect(
      eventLine({
        ...base,
        startsAt: "2026-11-15T02:00:00Z",
        venueName: "Galpão 42",
      }),
    ).toBe("Sáb 14.11 · 23h · Galpão 42")
  })

  it("leva o rascunho para o passo que falta", () => {
    expect(eventHref(base)).toBe("/painel/eventos/e1/informacoes")
    expect(eventHref({ ...base, startsAt: "2026-11-15T02:00:00Z" })).toBe(
      "/painel/eventos/e1/aparencia",
    )
    expect(
      eventHref({
        ...base,
        startsAt: "2026-11-15T02:00:00Z",
        flyerUrl: "x",
      }),
    ).toBe("/painel/eventos/e1/revisar")
    expect(eventHref({ ...base, status: "PUBLISHED" })).toBe(
      "/painel/eventos/e1",
    )
  })
})
