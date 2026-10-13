import { describe, expect, it } from "vitest"

import { checkoutSchema, formPath, toOrderBody } from "./checkout"

const valid = {
  buyer: {
    name: "Ana Souza",
    email: "ana@festa.test",
    phone: "",
    cpf: "529.982.247-25",
  },
  tickets: [
    {
      batchId: "b1",
      halfPrice: true,
      holderName: "Bia Lima",
      holderCpf: "529.982.247-25",
      halfPriceReason: "STUDENT",
    },
  ],
  adult: true,
  terms: true as const,
}

describe("checkout schema", () => {
  it("aceita pedido completo e monta o corpo só com dígitos no CPF", () => {
    expect(checkoutSchema(true).safeParse(valid).success).toBe(true)
    const body = toOrderBody("calourada", valid)
    expect(body.buyer.cpf).toBe("52998224725")
    expect(body.tickets[0]).toEqual({
      batchId: "b1",
      holderName: "Bia Lima",
      holderCpf: "52998224725",
      halfPriceReason: "STUDENT",
    })
  })

  it("exige benefício na meia, CPF válido e declaração 18+", () => {
    const result = checkoutSchema(true).safeParse({
      ...valid,
      adult: false,
      tickets: [
        {
          ...valid.tickets[0],
          holderCpf: "111.111.111-11",
          halfPriceReason: "",
        },
      ],
    })
    expect(result.success).toBe(false)
    const paths = result.error!.issues.map((i) => i.path.join("."))
    expect(paths).toEqual(
      expect.arrayContaining([
        "adult",
        "tickets.0.holderCpf",
        "tickets.0.halfPriceReason",
      ]),
    )
  })

  it("festa sem restrição de idade não pede a declaração", () => {
    expect(
      checkoutSchema(false).safeParse({ ...valid, adult: false }).success,
    ).toBe(true)
  })

  it("traduz o campo da API para o formulário", () => {
    expect(formPath("tickets[2].holderCpf")).toBe("tickets.2.holderCpf")
    expect(formPath("buyer.cpf")).toBe("buyer.cpf")
  })
})
