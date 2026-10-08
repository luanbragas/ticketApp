import { describe, expect, it } from "vitest"

import { endInstant, eventInfoSchema, toInstant, toLocalParts } from "./event"

describe("datas do evento em São Paulo", () => {
  it("converte data e hora de parede para instante UTC", () => {
    expect(toInstant("2026-11-14", "23:00")).toBe("2026-11-15T02:00:00.000Z")
  })

  it("joga o fim para o dia seguinte quando a festa vira a noite", () => {
    expect(endInstant("2026-11-14", "23:00", "05:00")).toBe(
      "2026-11-15T08:00:00.000Z",
    )
    expect(endInstant("2026-11-14", "14:00", "20:00")).toBe(
      "2026-11-14T23:00:00.000Z",
    )
  })

  it("volta do instante para os campos do formulário", () => {
    expect(toLocalParts("2026-11-15T02:00:00Z")).toEqual({
      date: "2026-11-14",
      time: "23:00",
    })
  })
})

describe("eventInfoSchema", () => {
  const valid = {
    name: "Calourada Med 26",
    date: "2026-11-14",
    startTime: "23:00",
    endTime: "05:00",
    venueName: "Galpão 42",
    address: "",
    city: "Belo Horizonte",
    description: "",
    adultsOnly: true,
    hasOpenBar: true,
  }

  it("aceita evento completo", () => {
    expect(eventInfoSchema.safeParse(valid).success).toBe(true)
  })

  it("exige 18+ quando tem open bar", () => {
    const result = eventInfoSchema.safeParse({ ...valid, adultsOnly: false })
    expect(result.success).toBe(false)
    expect(result.error?.issues[0].path).toEqual(["adultsOnly"])
  })

  it("exige data, horário e local", () => {
    const result = eventInfoSchema.safeParse({
      ...valid,
      date: "",
      startTime: "25:00",
      venueName: " ",
    })
    expect(result.error?.issues.map((i) => i.path[0])).toEqual([
      "date",
      "startTime",
      "venueName",
    ])
  })
})
