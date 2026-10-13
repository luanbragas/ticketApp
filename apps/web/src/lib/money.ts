/** Dinheiro sempre em centavos inteiros (CLAUDE.md regra 1); reais só na tela. */

const BRL = new Intl.NumberFormat("pt-BR", {
  style: "currency",
  currency: "BRL",
})

/** 3000 → "R$ 30,00". */
export function formatCents(cents: number): string {
  // Intl usa espaço inseparável depois do "R$"; espaço comum facilita teste e cópia.
  return BRL.format(cents / 100).replace(/ /g, " ")
}

/** 3000 → "30,00", para preencher o campo de preço. */
export function centsToInput(cents: number): string {
  const reais = Math.trunc(cents / 100)
  const rest = String(cents % 100).padStart(2, "0")
  return `${reais},${rest}`
}

/**
 * Texto digitado em reais → centavos, sem passar por número decimal.
 * Aceita "30", "30,5", "30,50", "1.234,56" e "R$ 30". Inválido → null.
 */
export function parseReais(input: string): number | null {
  const clean = input.replace(/R\$/i, "").replace(/\s/g, "")
  const match = /^(\d{1,3}(?:\.\d{3})+|\d+)(?:,(\d{1,2}))?$/.exec(clean)
  if (!match) return null
  const reais = Number(match[1].replace(/\./g, ""))
  const centavos = Number((match[2] ?? "0").padEnd(2, "0"))
  const total = reais * 100 + centavos
  return Number.isSafeInteger(total) ? total : null
}
