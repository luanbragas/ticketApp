import { describe, expect, it } from "vitest"

import { ApiError, toApiError } from "./client"

describe("toApiError", () => {
  it("lê Problem Details com erros de campo", () => {
    const error = toApiError(400, {
      type: "https://festa.com/errors/validation",
      title: "Dados inválidos",
      status: 400,
      detail: "Confira os campos destacados.",
      errors: [{ field: "email", message: "E-mail inválido." }],
    })

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(400)
    expect(error.code).toBe("validation")
    expect(error.title).toBe("Dados inválidos")
    expect(error.message).toBe("Confira os campos destacados.")
    expect(error.errors).toEqual([
      { field: "email", message: "E-mail inválido." },
    ])
  })

  it("ignora erros de campo malformados", () => {
    const error = toApiError(400, {
      type: "https://festa.com/errors/validation",
      errors: [
        { field: "email" },
        null,
        "x",
        { field: "name", message: "Informe seu nome." },
      ],
    })

    expect(error.errors).toEqual([
      { field: "name", message: "Informe seu nome." },
    ])
  })

  it("tolera resposta que não é Problem Details", () => {
    const error = toApiError(502, "<html>Bad Gateway</html>")

    expect(error.status).toBe(502)
    expect(error.message).toBe("Algo deu errado. Tente novamente.")
    expect(error.errors).toEqual([])
  })
})
