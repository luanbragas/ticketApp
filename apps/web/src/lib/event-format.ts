/** Formatação das datas e do local do evento para exibição (sempre em America/Sao_Paulo). */

const TIME_ZONE = "America/Sao_Paulo"

const weekday = new Intl.DateTimeFormat("pt-BR", {
  weekday: "short",
  timeZone: TIME_ZONE,
})
const dayMonth = new Intl.DateTimeFormat("pt-BR", {
  day: "2-digit",
  month: "2-digit",
  timeZone: TIME_ZONE,
})
const hour = new Intl.DateTimeFormat("pt-BR", {
  hour: "2-digit",
  minute: "2-digit",
  hourCycle: "h23",
  timeZone: TIME_ZONE,
})
const longDate = new Intl.DateTimeFormat("pt-BR", {
  weekday: "long",
  day: "numeric",
  month: "long",
  timeZone: TIME_ZONE,
})

/** "2026-11-15T02:00:00Z" → "Sex 14.11". */
export function shortDay(iso: string): string {
  const d = new Date(iso)
  const name = weekday.format(d).replace(".", "")
  return `${name.charAt(0).toUpperCase()}${name.slice(1)} ${dayMonth.format(d).replace("/", ".")}`
}

/** "23:00" → "23h"; "23:30" → "23h30". */
function compactHour(iso: string): string {
  const [h, m] = hour.format(new Date(iso)).split(":")
  return m === "00" ? `${h}h` : `${h}h${m}`
}

/** Faixa de horário: "23h às 5h". */
export function hourRange(startsAt: string, endsAt: string | null): string {
  const start = compactHour(startsAt)
  if (!endsAt) return start
  return `${start} às ${compactHour(endsAt).replace(/^0/, "")}`
}

/** "sexta-feira, 14 de novembro" para descrições e metadados. */
export function longDay(iso: string): string {
  return longDate.format(new Date(iso))
}

/** Dia no calendário de São Paulo, como número comparável (AAAAMMDD). */
function calendarDay(date: Date): number {
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(date)
  return Date.UTC(
    Number(parts.slice(0, 4)),
    Number(parts.slice(5, 7)) - 1,
    Number(parts.slice(8, 10)),
  )
}

/** "Hoje", "Amanhã", "Em 38 dias"; nulo se já passou. */
export function countdownLabel(startsAt: string, now: Date): string | null {
  const days = Math.round(
    (calendarDay(new Date(startsAt)) - calendarDay(now)) / 86_400_000,
  )
  if (days < 0) return null
  if (days === 0) return "Hoje"
  if (days === 1) return "Amanhã"
  return `Em ${days} dias`
}

/** Link de busca no Google Maps (abre o app no celular), sem chave de API. */
export function mapsUrl(parts: (string | null | undefined)[]): string {
  const query = parts.filter(Boolean).join(", ")
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(query)}`
}
