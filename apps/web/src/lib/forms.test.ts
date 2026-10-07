import { describe, expect, it, vi } from "vitest"

import { ApiError } from "@/lib/api/client"

import { applyApiError, safeNext } from "./forms"

describe("safeNext", () => {
  it("aceita caminhos internos", () => {
    expect(safeNext("/convite")).toBe("/convite")
    expect(safeNext("/painel/equipe?x=1")).toBe("/painel/equipe?x=1")
  })

  it("bloqueia redirecionamento para outro site", () => {
    expect(safeNext("https://evil.example")).toBe("/painel")
    expect(safeNext("//evil.example")).toBe("/painel")
    expect(safeNext("/\\evil.example")).toBe("/painel")
    expect(safeNext("javascript:alert(1)")).toBe("/painel")
  })

  it("usa o padrão quando não há destino", () => {
    expect(safeNext(undefined)).toBe("/painel")
    expect(safeNext(null, "/")).toBe("/")
  })
})

describe("applyApiError", () => {
  it("leva erros de campo para o formulário e não mostra mensagem geral", () => {
    const setError = vi.fn()
    const error = new ApiError(
      400,
      "validation",
      "Dados inválidos",
      "Confira os campos.",
      [{ field: "email", message: "E-mail inválido." }],
    )

    const message = applyApiError(error, setError, ["email", "password"])

    expect(setError).toHaveBeenCalledWith("email", {
      message: "E-mail inválido.",
    })
    expect(message).toBeNull()
  })

  it("mostra a mensagem da API quando o erro não é de um campo do formulário", () => {
    const setError = vi.fn()
    const error = new ApiError(
      409,
      "https://festa.com/errors/email-already-registered",
      "E-mail já cadastrado",
      "Já existe uma conta com este e-mail.",
    )

    expect(applyApiError(error, setError, ["email"])).toBe(
      "Já existe uma conta com este e-mail.",
    )
    expect(setError).not.toHaveBeenCalled()
  })

  it("usa mensagem genérica para erros inesperados", () => {
    expect(applyApiError(new Error("boom"), vi.fn(), ["email"])).toBe(
      "Algo deu errado. Tente novamente.",
    )
  })
})
