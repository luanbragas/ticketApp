/**
 * Atribuição de promoter (ADR-009): o link /e/{slug}?p={code} grava um cookie de 7 dias por evento; o
 * último link clicado vence. O checkout manda o código e a API decide se ele vale.
 */

const SEVEN_DAYS = 7 * 24 * 60 * 60
const CODE = /^[a-z0-9]+(-[a-z0-9]+)*$/

export function promoterCookieName(slug: string): string {
  return `festa_p_${slug}`
}

/** Valor para document.cookie, ou nulo se o código não tem cara de código. */
export function promoterCookie(slug: string, code: string): string | null {
  const normalized = code.trim().toLowerCase()
  if (
    !CODE.test(normalized) ||
    normalized.length < 3 ||
    normalized.length > 30
  ) {
    return null
  }
  return `${promoterCookieName(slug)}=${normalized}; Max-Age=${SEVEN_DAYS}; Path=/; SameSite=Lax`
}

/** Código guardado para o evento, a partir de document.cookie. */
export function promoterFromCookies(
  cookies: string,
  slug: string,
): string | null {
  const prefix = `${promoterCookieName(slug)}=`
  const found = cookies
    .split(";")
    .map((part) => part.trim())
    .find((part) => part.startsWith(prefix))
  if (!found) return null
  const code = found.slice(prefix.length)
  return CODE.test(code) ? code : null
}
