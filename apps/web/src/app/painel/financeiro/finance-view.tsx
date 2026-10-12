"use client"

import { useQuery } from "@tanstack/react-query"
import Link from "next/link"

import { FormError } from "@/components/form/form-error"
import { Skeleton } from "@/components/ui/skeleton"
import { getFinanceOverview } from "@/lib/api/finance"
import {
  EVENT_STATUS_LABELS,
  type EventFinance,
  type FinanceTotals,
} from "@/lib/api/types"
import { formatCents } from "@/lib/money"

const DAY = new Intl.DateTimeFormat("pt-BR", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  timeZone: "America/Sao_Paulo",
})

/**
 * Financeiro básico (PLAN.md M9). Com o split do Mercado Pago o valor dos ingressos cai direto na conta do
 * produtor a cada venda; aqui é o resumo do que entrou e do que saiu em estornos.
 */
export function FinanceView({ organizationId }: { organizationId: string }) {
  const finance = useQuery({
    queryKey: ["finance", organizationId],
    queryFn: () => getFinanceOverview(organizationId),
  })

  if (finance.isPending) {
    return (
      <div aria-busy="true" className="grid gap-3">
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-16 w-full" />
      </div>
    )
  }
  if (finance.isError) return <FormError message={finance.error.message} />

  const { events, totals } = finance.data
  if (events.length === 0) {
    return (
      <p className="text-muted-foreground">
        Nenhuma venda ainda. Quando a primeira cair, o resumo aparece aqui.
      </p>
    )
  }

  return (
    <div className="grid gap-8">
      <Summary totals={totals} />

      <section aria-labelledby="por-evento" className="grid gap-2">
        <h2
          id="por-evento"
          className="border-b border-foreground pb-2 font-display text-3xl leading-none font-black uppercase"
        >
          Por evento
        </h2>
        <ul>
          {events.map((event) => (
            <EventRow key={event.eventId} event={event} />
          ))}
        </ul>
      </section>

      <p className="text-sm text-muted-foreground">
        Os valores são antes das tarifas do Mercado Pago, que aparecem no
        extrato da sua conta lá. A plataforma não segura dinheiro: o valor dos
        ingressos vai direto para a conta conectada em{" "}
        <Link
          href="/painel/organizacao/recebimentos"
          className="font-bold text-foreground underline underline-offset-4"
        >
          Recebimentos
        </Link>
        , e o prazo para liberar é o do Mercado Pago.
      </p>
    </div>
  )
}

function Summary({ totals }: { totals: FinanceTotals }) {
  return (
    <div className="grid gap-4">
      <div>
        <p className="text-sm text-muted-foreground">Líquido dos ingressos</p>
        <p className="font-display text-6xl leading-none font-black text-primary tabular-nums">
          {formatCents(totals.netCents)}
        </p>
      </div>
      <dl className="grid gap-1 text-sm">
        <Line
          label={`Vendas (${totals.ticketsSold} ${totals.ticketsSold === 1 ? "ingresso" : "ingressos"})`}
          value={formatCents(totals.ticketSalesCents)}
        />
        <Line
          label={`Reembolsos e estornos (${totals.ticketsRefunded})`}
          value={
            totals.ticketRefundsCents > 0
              ? `− ${formatCents(totals.ticketRefundsCents)}`
              : formatCents(0)
          }
        />
        {totals.chargebacks > 0 && (
          <p className="text-[#ffb020]">
            {totals.chargebacks}{" "}
            {totals.chargebacks === 1
              ? "contestação de cartão"
              : "contestações de cartão"}
            . Veja os detalhes no Mercado Pago.
          </p>
        )}
        <Line
          muted
          label="Taxa de serviço (paga pelo comprador, fica com a plataforma)"
          value={formatCents(
            totals.serviceFeeCents - totals.serviceFeeRefundsCents,
          )}
        />
      </dl>
    </div>
  )
}

function Line({
  label,
  value,
  muted = false,
}: {
  label: string
  value: string
  muted?: boolean
}) {
  return (
    <div
      className={
        muted
          ? "flex justify-between gap-3 border-t pt-2 text-muted-foreground"
          : "flex justify-between gap-3"
      }
    >
      <dt>{label}</dt>
      <dd className="shrink-0 tabular-nums">{value}</dd>
    </div>
  )
}

function EventRow({ event }: { event: EventFinance }) {
  const t = event.totals
  return (
    <li className="border-b">
      <Link
        href={`/painel/eventos/${event.eventId}`}
        className="flex min-h-16 items-center justify-between gap-3 py-2 hover:bg-muted"
      >
        <span className="min-w-0">
          <span className="block truncate font-bold">{event.name}</span>
          <span className="block text-sm text-muted-foreground">
            {event.startsAt ? DAY.format(new Date(event.startsAt)) : "Sem data"}{" "}
            · {EVENT_STATUS_LABELS[event.status]} ·{" "}
            {t.ticketsSold - t.ticketsRefunded}{" "}
            {t.ticketsSold - t.ticketsRefunded === 1 ? "ingresso" : "ingressos"}
            {t.refundedOrders + t.chargebacks > 0 &&
              ` · ${t.refundedOrders + t.chargebacks} estornado${t.refundedOrders + t.chargebacks === 1 ? "" : "s"}`}
          </span>
        </span>
        <span className="shrink-0 font-extrabold tabular-nums">
          {formatCents(t.netCents)}
        </span>
      </Link>
    </li>
  )
}
