import { describe, expect, it } from "vitest"

import { countdownLabel, hourRange, mapsUrl, shortDay } from "./event-format"

// 14/11/2026 23:00 em São Paulo = 15/11 02:00 UTC.
const STARTS = "2026-11-15T02:00:00Z"
const ENDS = "2026-11-15T08:00:00Z"

describe("event-format", () => {
  it("mostra o dia curto no fuso de São Paulo", () => {
    expect(shortDay(STARTS)).toBe("Sáb 14.11")
  })

  it("mostra a faixa de horário que vira a noite", () => {
    expect(hourRange(STARTS, ENDS)).toBe("23h às 5h")
    expect(hourRange("2026-11-14T23:30:00Z", null)).toBe("20h30")
  })

  it("conta os dias pelo calendário de São Paulo", () => {
    // 14/11 às 22h em SP ainda é "hoje" para a festa das 23h.
    expect(countdownLabel(STARTS, new Date("2026-11-15T01:00:00Z"))).toBe(
      "Hoje",
    )
    expect(countdownLabel(STARTS, new Date("2026-11-13T15:00:00Z"))).toBe(
      "Amanhã",
    )
    expect(countdownLabel(STARTS, new Date("2026-10-07T15:00:00Z"))).toBe(
      "Em 38 dias",
    )
    expect(countdownLabel(STARTS, new Date("2026-11-16T15:00:00Z"))).toBeNull()
  })

  it("monta link de mapa sem campos vazios", () => {
    expect(mapsUrl(["Galpão 42", null, "Belo Horizonte"])).toBe(
      "https://www.google.com/maps/search/?api=1&query=Galp%C3%A3o%2042%2C%20Belo%20Horizonte",
    )
  })
})
