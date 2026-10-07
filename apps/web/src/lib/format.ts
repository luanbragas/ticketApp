const brl = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
})

/** Formata um valor em centavos (como vem da API) para exibição em reais. */
export function formatCents(cents: number): string {
  if (!Number.isSafeInteger(cents)) {
    throw new RangeError(`Valor em centavos inválido: ${cents}`)
  }
  return brl.format(cents / 100)
}
