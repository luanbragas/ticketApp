import { describe, expect, it } from "vitest"

import { googleCalendarUrl, icsContent } from "./calendar"

const event = {
  title: "Calourada; Med, 26",
  startsAt: "2026-11-15T02:00:00Z",
  endsAt: "2026-11-15T08:00:00Z",
  location: "Galpão 42, Belo Horizonte",
  details: "Ingresso: festa.com/t/abc",
}

describe("calendar", () => {
  it("monta o link do Google Agenda em UTC", () => {
    const url = new URL(googleCalendarUrl(event))
    expect(url.hostname).toBe("calendar.google.com")
    expect(url.searchParams.get("dates")).toBe(
      "20261115T020000Z/20261115T080000Z",
    )
    expect(url.searchParams.get("text")).toBe("Calourada; Med, 26")
  })

  it("gera .ics com texto escapado", () => {
    const ics = icsContent(event, "t1")
    expect(ics).toContain("DTSTART:20261115T020000Z")
    expect(ics).toContain("SUMMARY:Calourada\\; Med\\, 26")
    expect(ics).toContain("LOCATION:Galpão 42\\, Belo Horizonte")
    expect(ics.split("\r\n")[0]).toBe("BEGIN:VCALENDAR")
  })
})
