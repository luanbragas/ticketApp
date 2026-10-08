"use client"

import { useQuery } from "@tanstack/react-query"
import { PlusIcon } from "lucide-react"
import Link from "next/link"
import { usePathname, useRouter, useSearchParams } from "next/navigation"

import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { listEvents } from "@/lib/api/events"
import { EVENT_STATUS_LABELS, type EventStatus } from "@/lib/api/types"
import {
  EVENT_FILTERS,
  eventHref,
  eventLine,
  filterFrom,
} from "@/lib/event-list"
import { cn } from "@/lib/utils"

const STATUS_STYLE: Record<EventStatus, string> = {
  PUBLISHED: "bg-primary text-primary-foreground",
  DRAFT: "bg-secondary text-foreground",
  ENDED: "bg-foreground text-background",
  CANCELLED: "bg-muted text-muted-foreground",
}

export function EventsView({
  organizationId,
  canEdit,
}: {
  organizationId: string
  canEdit: boolean
}) {
  const router = useRouter()
  const pathname = usePathname()
  const params = useSearchParams()
  const filter = filterFrom(params.get("status"))
  const events = useQuery({
    queryKey: ["events", organizationId, filter.status ?? "all"],
    queryFn: () => listEvents(organizationId, filter.status),
  })

  const choose = (value: string) => {
    const next = new URLSearchParams(params)
    if (value === "todos") next.delete("status")
    else next.set("status", value)
    const query = next.toString()
    router.replace(query ? `${pathname}?${query}` : pathname, { scroll: false })
  }

  return (
    <div className="grid gap-4">
      <nav
        aria-label="Filtrar eventos"
        className="flex gap-5 overflow-x-auto border-b"
      >
        {EVENT_FILTERS.map((f) => {
          const active = f.value === filter.value
          return (
            <button
              key={f.value}
              type="button"
              onClick={() => choose(f.value)}
              aria-pressed={active}
              className={cn(
                "-mb-px min-h-11 shrink-0 border-b-2 text-sm font-extrabold",
                active
                  ? "border-primary text-foreground"
                  : "border-transparent text-muted-foreground hover:text-foreground",
              )}
            >
              {f.label}
            </button>
          )
        })}
      </nav>

      {events.isPending && (
        <div aria-busy="true" className="grid">
          {[0, 1, 2].map((i) => (
            <div key={i} className="flex items-center gap-4 border-b py-3">
              <Skeleton className="h-20 w-15 shrink-0" />
              <div className="grid flex-1 gap-2">
                <Skeleton className="h-5 w-1/2" />
                <Skeleton className="h-4 w-1/3" />
              </div>
            </div>
          ))}
        </div>
      )}

      {events.isError && (
        <div role="alert" className="border border-l-4 border-destructive p-4">
          <p className="font-bold text-destructive">
            Não deu pra carregar os eventos
          </p>
          <p className="mt-1 text-sm">{events.error.message}</p>
          <Button
            variant="outline"
            className="mt-3 h-11"
            onClick={() => events.refetch()}
          >
            Tentar de novo
          </Button>
        </div>
      )}

      {events.isSuccess && events.data.items.length === 0 && (
        <Empty filtered={filter.value !== "todos"} canEdit={canEdit} />
      )}

      {events.isSuccess && events.data.items.length > 0 && (
        <ul className="border-t border-foreground">
          {events.data.items.map((event) => (
            <li key={event.id} className="border-b">
              <Link
                href={canEdit ? eventHref(event) : `/e/${event.slug}`}
                className="flex items-center gap-4 py-3 hover:bg-muted"
              >
                <span className="relative h-20 w-15 shrink-0 overflow-hidden bg-muted">
                  {event.flyerUrl && (
                    // eslint-disable-next-line @next/next/no-img-element -- miniatura do bucket do produtor
                    <img
                      src={event.flyerUrl}
                      alt=""
                      className="size-full object-cover"
                    />
                  )}
                </span>
                <span className="min-w-0 flex-1">
                  <span className="block truncate font-display text-2xl leading-tight font-black uppercase">
                    {event.name}
                  </span>
                  <span className="block truncate text-sm text-muted-foreground">
                    {eventLine(event)}
                  </span>
                </span>
                <span
                  className={cn(
                    "shrink-0 px-2 py-0.5 text-xs font-extrabold tracking-wide uppercase",
                    STATUS_STYLE[event.status],
                  )}
                >
                  {EVENT_STATUS_LABELS[event.status]}
                </span>
              </Link>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

function Empty({ filtered, canEdit }: { filtered: boolean; canEdit: boolean }) {
  if (filtered) {
    return (
      <p className="py-10 text-muted-foreground">
        Nenhum evento com esse status.
      </p>
    )
  }
  return (
    <div className="py-10">
      <p className="font-display text-6xl leading-[0.9] font-black text-[#6b6b6b] uppercase">
        Primeira
        <br />
        festa?
      </p>
      <p className="mt-3 max-w-sm">
        Em 3 passos: informações, flyer e publicar. Dá pra sair no meio que fica
        salvo como rascunho.
      </p>
      {canEdit && (
        <Link
          href="/painel/eventos/novo"
          className="mt-6 flex h-14 max-w-sm items-center justify-between bg-primary px-5 text-primary-foreground"
        >
          <span className="font-display text-2xl font-black uppercase">
            Criar evento
          </span>
          <PlusIcon className="size-5" aria-hidden />
        </Link>
      )}
    </div>
  )
}
