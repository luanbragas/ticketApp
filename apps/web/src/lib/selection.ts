/**
 * Seleção de ingressos no endereço do checkout: "?i=<lote>.<qtd>,<lote>.<qtd>". Fica no link para o
 * botão voltar e o recarregar não perderem a escolha.
 */
export type Selection = Record<string, number>

const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

export function parseSelection(param: string | null): Selection {
  const selection: Selection = {}
  for (const part of (param ?? "").split(",")) {
    const [batchId, qty] = part.split(".")
    const quantity = Number(qty)
    if (
      UUID.test(batchId ?? "") &&
      Number.isInteger(quantity) &&
      quantity > 0 &&
      quantity <= 20
    ) {
      selection[batchId.toLowerCase()] = quantity
    }
  }
  return selection
}

export function serializeSelection(selection: Selection): string {
  return Object.entries(selection)
    .filter(([, quantity]) => quantity > 0)
    .map(([batchId, quantity]) => `${batchId}.${quantity}`)
    .join(",")
}

export function selectionItems(selection: Selection) {
  return Object.entries(selection)
    .filter(([, quantity]) => quantity > 0)
    .map(([batchId, quantity]) => ({ batchId, quantity }))
}

export function ticketCount(selection: Selection): number {
  return Object.values(selection).reduce((sum, n) => sum + n, 0)
}

/** Chave do pedido: aleatória, 32 caracteres hex (o servidor guarda só o hash). */
export function newOrderKey(): string {
  return crypto.randomUUID().replace(/-/g, "")
}
