import { z } from "zod"

import type { PlaceOrderBody } from "@/lib/api/orders"
import type { HalfPriceReason } from "@/lib/api/types"
import { cpfDigits, isValidCpf } from "@/lib/cpf"

const cpf = z.string().refine(isValidCpf, "CPF inválido. Confira os números.")

/** Formulário de checkout; {@code adultsOnly} vem do evento (18+ exige a declaração). */
export function checkoutSchema(adultsOnly: boolean) {
  return z.object({
    buyer: z.object({
      name: z
        .string()
        .trim()
        .min(3, "Informe seu nome completo.")
        .max(120, "Nome muito longo."),
      email: z.email("E-mail inválido.").max(254, "E-mail muito longo."),
      phone: z
        .string()
        .refine(
          (v) => v.trim() === "" || /^[0-9+()\s-]{10,20}$/.test(v),
          "Celular inválido.",
        ),
      cpf,
    }),
    tickets: z.array(
      z
        .object({
          batchId: z.string(),
          halfPrice: z.boolean(),
          holderName: z
            .string()
            .trim()
            .min(3, "Informe o nome do titular.")
            .max(120, "Nome muito longo."),
          holderCpf: cpf,
          halfPriceReason: z.string(),
        })
        .refine((t) => !t.halfPrice || t.halfPriceReason !== "", {
          path: ["halfPriceReason"],
          message: "Escolha o benefício da meia-entrada.",
        }),
    ),
    adult: z
      .boolean()
      .refine(
        (v) => !adultsOnly || v,
        "Confirme que todos os titulares têm 18 anos ou mais.",
      ),
    terms: z.literal(true, "É preciso aceitar para continuar."),
  })
}

export type CheckoutInput = z.infer<ReturnType<typeof checkoutSchema>>

export function toOrderBody(
  slug: string,
  input: CheckoutInput,
): PlaceOrderBody {
  return {
    event: slug,
    buyer: {
      name: input.buyer.name.trim(),
      email: input.buyer.email.trim(),
      phone: input.buyer.phone.trim(),
      cpf: cpfDigits(input.buyer.cpf),
    },
    tickets: input.tickets.map((t) => ({
      batchId: t.batchId,
      holderName: t.holderName.trim(),
      holderCpf: cpfDigits(t.holderCpf),
      halfPriceReason: t.halfPrice
        ? (t.halfPriceReason as HalfPriceReason)
        : null,
    })),
    adultDeclared: input.adult,
    termsAccepted: input.terms,
  }
}

/** Campo da API ("tickets[1].holderCpf") → caminho do formulário ("tickets.1.holderCpf"). */
export function formPath(apiField: string): string {
  return apiField.replace(/\[(\d+)\]/g, ".$1")
}
