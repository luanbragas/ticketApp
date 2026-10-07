import { describe, expect, it } from "vitest"

import { formatCents } from "./format"

// Intl usa espaço não separável entre "R$" e o valor.
const normalize = (s: string) => s.replace(/ /g, " ")

describe("formatCents", () => {
  it("formata centavos em reais", () => {
    expect(normalize(formatCents(123456))).toBe("R$ 1.234,56")
  })

  it("formata zero e valores menores que um real", () => {
    expect(normalize(formatCents(0))).toBe("R$ 0,00")
    expect(normalize(formatCents(5))).toBe("R$ 0,05")
  })

  it("rejeita valores que não são centavos inteiros", () => {
    expect(() => formatCents(10.5)).toThrow(RangeError)
    expect(() => formatCents(Number.NaN)).toThrow(RangeError)
  })
})
