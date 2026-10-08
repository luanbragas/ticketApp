import { describe, expect, it } from "vitest"

import { formatCpf, isValidCpf } from "./cpf"

describe("cpf", () => {
  it("confere os dígitos verificadores", () => {
    expect(isValidCpf("529.982.247-25")).toBe(true)
    expect(isValidCpf("52998224725")).toBe(true)
    expect(isValidCpf("529.982.247-26")).toBe(false)
    expect(isValidCpf("111.111.111-11")).toBe(false)
    expect(isValidCpf("123")).toBe(false)
  })

  it("formata enquanto digita", () => {
    expect(formatCpf("529")).toBe("529")
    expect(formatCpf("5299")).toBe("529.9")
    expect(formatCpf("5299822")).toBe("529.982.2")
    expect(formatCpf("52998224725")).toBe("529.982.247-25")
    expect(formatCpf("529.982.247-2599")).toBe("529.982.247-25")
  })
})
