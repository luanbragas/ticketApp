import { describe, expect, it } from "vitest"

import { centsToInput, formatCents, parseReais } from "./money"

describe("money", () => {
  it("formata centavos em reais", () => {
    expect(formatCents(3000)).toBe("R$ 30,00")
    expect(formatCents(123456)).toBe("R$ 1.234,56")
    expect(formatCents(5)).toBe("R$ 0,05")
  })

  it("lê o que a pessoa digita sem erro de arredondamento", () => {
    expect(parseReais("30")).toBe(3000)
    expect(parseReais("30,5")).toBe(3050)
    expect(parseReais("30,50")).toBe(3050)
    expect(parseReais("R$ 1.234,56")).toBe(123456)
    expect(parseReais("0,29")).toBe(29)
  })

  it("recusa formato que não é preço", () => {
    expect(parseReais("")).toBeNull()
    expect(parseReais("30.5")).toBeNull()
    expect(parseReais("30,555")).toBeNull()
    expect(parseReais("-10")).toBeNull()
    expect(parseReais("abc")).toBeNull()
  })

  it("volta para o campo do jeito que foi digitado", () => {
    expect(centsToInput(3050)).toBe("30,50")
    expect(centsToInput(5)).toBe("0,05")
    expect(parseReais(centsToInput(123456))).toBe(123456)
  })
})
