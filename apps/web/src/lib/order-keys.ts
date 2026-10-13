/**
 * Chave de cada pedido neste navegador (ADR-007): sem ela, a página do pedido não consegue ler o status.
 * Fica no localStorage para sobreviver a recarregar e trocar de aba; navegação privada pode bloquear,
 * e aí o comprador acompanha pelo e-mail (M6).
 */
const PREFIX = "festa:order:"

export function rememberOrderKey(orderId: string, key: string) {
  try {
    localStorage.setItem(PREFIX + orderId, key)
  } catch {
    // Armazenamento indisponível: a página do pedido explica o que fazer.
  }
}

export function orderKey(orderId: string): string | null {
  try {
    return localStorage.getItem(PREFIX + orderId)
  } catch {
    return null
  }
}
