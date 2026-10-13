"use client"

import { useQuery } from "@tanstack/react-query"
import { ArrowUpRightIcon } from "lucide-react"
import Link from "next/link"

import { FormError } from "@/components/form/form-error"
import { Skeleton } from "@/components/ui/skeleton"
import { getDashboard } from "@/lib/api/checkin"
import { getEvent } from "@/lib/api/events"
import { EVENT_STATUS_LABELS } from "@/lib/api/types"
import { hourRange, shortDay } from "@/lib/event-format"
import { formatCents } from "@/lib/money"

import { SalesByDay } from "./sales-by-day"

/** Painel do evento (PLAN.md M8): vendas, estoque, entradas, vendas por dia e atalhos. */
export function EventHome({
  organizationId,
  eventId,
}: {
  organizationId: string
  eventId: string
}) {
  const event = useQuery({
    queryKey: ["event", organizationId, eventId],
    queryFn: () => getEvent(organizationId, eventId),
  })
  const dashboard = useQuery({
    queryKey: ["dashboard", organizationId, eventId],
    queryFn: () => getDashboard(organizationId, eventId),
    refetchInterval: 30_000,
  })

  if (event.isPending || dashboard.isPending) {
    return (
      <div aria-busy="true" className="grid gap-4">
        <Skeleton className="h-14 w-2/3" />
        <Skeleton className="h-28 w-full" />
        <Skeleton className="h-48 w-full" />
      </div>
    )
  }
  if (event.isError) return <FormError message={event.error.message} />
  if (dashboard.isError) return <FormError message={dashboard.error.message} />

  const e = event.data
  const d = dashboard.data
  const soldShare = d.capacity > 0 ? Math.min(1, d.sold / d.capacity) : 0
  const base = `/painel/eventos/${e.id}`

  return (
    <div className="grid gap-8">
      <div>
        <Link
          href="/painel/eventos"
          className="text-sm font-bold text-muted-foreground hover:text-foreground"
        >
          ← Eventos
        </Link>
        <p className="mt-4 text-sm text-muted-foreground">
          {e.startsAt
            ? `${shortDay(e.startsAt)} · ${hourRange(e.startsAt, e.endsAt)}`
            : "Sem data"}
          {e.venueName ? ` · ${e.venueName}` : ""}
        </p>
        <h1 className="mt-1 font-display text-5xl leading-[0.9] font-black uppercase">
          {e.name}
        </h1>
        <div className="mt-3 flex flex-wrap items-center gap-3">
          <span className="bg-primary px-2 py-0.5 text-xs font-extrabold tracking-wide text-primary-foreground uppercase">
            {EVENT_STATUS_LABELS[e.status]}
          </span>
          <a
            href={`/e/${e.slug}`}
            target="_blank"
            rel="noopener"
            className="flex items-center gap-1 text-sm font-bold underline underline-offset-4"
          >
            Página pública
            <ArrowUpRightIcon className="size-4" aria-hidden />
          </a>
        </div>
      </div>

      <section
        aria-label="Resumo"
        className="grid gap-px border-y bg-border sm:grid-cols-3"
      >
        <Stat
          label="Vendidos"
          value={`${d.sold}`}
          detail={`de ${d.capacity} · ${Math.round(soldShare * 100)}%`}
        >
          <div className="mt-2 h-1.5 bg-secondary" aria-hidden>
            <div
              className="h-full bg-primary"
              style={{ width: `${soldShare * 100}%` }}
            />
          </div>
        </Stat>
        <Stat
          label="Receita"
          value={formatCents(d.revenueCents)}
          detail={`${d.paidOrders} ${d.paidOrders === 1 ? "pedido pago" : "pedidos pagos"}${d.pendingOrders ? ` · ${d.pendingOrders} aguardando` : ""}`}
        />
        <Stat
          label="Entraram"
          value={`${d.checkedIn}`}
          detail={`de ${d.issued} ingressos emitidos`}
        />
      </section>

      <SalesByDay days={d.byDay} />

      <nav aria-label="Do evento" className="grid border-t border-foreground">
        <Shortcut
          href={`/checkin/${e.id}`}
          title="Portaria"
          detail="Check-in com o celular, funciona sem internet"
        />
        <Shortcut
          href={`${base}/participantes`}
          title="Participantes"
          detail="Busca por nome ou CPF e quem já entrou"
        />
        <Shortcut
          href={`${base}/promoters`}
          title="Promoters"
          detail="Links de divulgação e vendas de cada um"
        />
        <Shortcut
          href={`${base}/ingressos`}
          title="Ingressos"
          detail="Tipos, lotes e regras de venda"
        />
        <Shortcut
          href={`${base}/informacoes`}
          title="Editar evento"
          detail="Informações, flyer e lineup"
        />
      </nav>
    </div>
  )
}

function Stat({
  label,
  value,
  detail,
  children,
}: {
  label: string
  value: string
  detail: string
  children?: React.ReactNode
}) {
  return (
    <div className="bg-background py-4 sm:px-4 sm:first:pl-0">
      <p className="text-xs font-bold text-muted-foreground">{label}</p>
      <p className="mt-0.5 font-display text-4xl leading-none font-black">
        {value}
      </p>
      <p className="mt-1 text-sm text-muted-foreground">{detail}</p>
      {children}
    </div>
  )
}

function Shortcut({
  href,
  title,
  detail,
}: {
  href: string
  title: string
  detail: string
}) {
  return (
    <Link
      href={href}
      className="flex min-h-16 items-center justify-between gap-3 border-b py-3 hover:bg-muted"
    >
      <span className="min-w-0">
        <span className="block font-display text-2xl leading-tight font-black uppercase">
          {title}
        </span>
        <span className="block text-sm text-muted-foreground">{detail}</span>
      </span>
      <span aria-hidden className="text-xl">
        →
      </span>
    </Link>
  )
}
