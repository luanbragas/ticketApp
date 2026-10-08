import type { EventStatus, EventSummary } from "@/lib/api/types"

import { hourRange, shortDay } from "./event-format"

/** Abas da lista de eventos; "todos" não manda filtro para a API. */
export const EVENT_FILTERS = [
  { value: "todos", label: "Todos", status: undefined },
  { value: "publicados", label: "Publicados", status: "PUBLISHED" },
  { value: "rascunhos", label: "Rascunhos", status: "DRAFT" },
  { value: "encerrados", label: "Encerrados", status: "ENDED" },
] as const satisfies readonly {
  value: string
  label: string
  status: EventStatus | undefined
}[]

export type EventFilter = (typeof EVENT_FILTERS)[number]

/** Lê o filtro do ?status= da URL; valor desconhecido cai em "todos". */
export function filterFrom(param: string | null): EventFilter {
  return EVENT_FILTERS.find((f) => f.value === param) ?? EVENT_FILTERS[0]
}

/** Linha de apoio: "Sáb 12.12 · 23h · Galpão 42" ou o que falta no rascunho. */
export function eventLine(event: EventSummary): string {
  if (!event.startsAt) return "Sem data ainda"
  const when = `${shortDay(event.startsAt)} · ${hourRange(event.startsAt, null)}`
  return event.venueName ? `${when} · ${event.venueName}` : when
}

/** Para onde a linha leva: rascunho continua de onde parou; o resto abre a revisão. */
export function eventHref(event: EventSummary): string {
  if (event.status === "DRAFT" && !event.startsAt) {
    return `/painel/eventos/${event.id}/informacoes`
  }
  if (event.status === "DRAFT" && !event.flyerUrl) {
    return `/painel/eventos/${event.id}/aparencia`
  }
  return `/painel/eventos/${event.id}/revisar`
}
