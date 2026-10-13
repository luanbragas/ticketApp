"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { CheckIcon, MinusIcon } from "lucide-react"
import Link from "next/link"

import { FormError } from "@/components/form/form-error"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { getEvent, publishEvent } from "@/lib/api/events"
import { getCatalog } from "@/lib/api/tickets"
import {
  EVENT_STATUS_LABELS,
  type EventDetail,
  type TicketCatalog,
} from "@/lib/api/types"
import { formatCents } from "@/lib/money"
import { cn } from "@/lib/utils"

const when = new Intl.DateTimeFormat("pt-BR", {
  weekday: "short",
  day: "2-digit",
  month: "2-digit",
  hour: "2-digit",
  minute: "2-digit",
  timeZone: "America/Sao_Paulo",
})

type Item = {
  label: string
  detail: string
  ok: boolean
  href?: string
  blocking: boolean
}

function ticketsItem(event: EventDetail, catalog: TicketCatalog): Item {
  const open = catalog.types.flatMap((t) =>
    t.batches.filter((b) => b.status === "SCHEDULED" || b.status === "ON_SALE"),
  )
  const types = catalog.types.filter((t) => t.batches.length > 0).length
  const lowest = open.length ? Math.min(...open.map((b) => b.priceCents)) : 0
  return {
    label: "Ingressos",
    detail: open.length
      ? `${types} ${types === 1 ? "tipo" : "tipos"} · ${open.length} ${open.length === 1 ? "lote" : "lotes"} · a partir de ${formatCents(lowest)}`
      : "Falta criar pelo menos um lote",
    ok: open.length > 0,
    href: `/painel/eventos/${event.id}/ingressos`,
    blocking: true,
  }
}

function quotaItem(event: EventDetail, catalog: TicketCatalog): Item[] {
  const quota = catalog.halfPriceQuota
  if (quota.total === 0) return []
  return [
    {
      label: "Meia-entrada",
      detail: quota.met
        ? `${quota.halfPrice} de ${quota.total} ingressos são meia`
        : `Abaixo da cota de ${quota.percent}%: faltam ${quota.minimum - quota.halfPrice} de meia`,
      ok: quota.met,
      href: `/painel/eventos/${event.id}/ingressos`,
      blocking: false,
    },
  ]
}

function checklist(event: EventDetail, catalog: TicketCatalog): Item[] {
  const hasInfo = !!(event.startsAt && event.endsAt && event.venueName)
  return [
    {
      label: "Informações",
      detail: hasInfo
        ? `${when.format(new Date(event.startsAt!))} · ${event.venueName}`
        : "Falta data, horário ou local",
      ok: hasInfo,
      href: `/painel/eventos/${event.id}/informacoes`,
      blocking: true,
    },
    {
      label: "Aparência",
      detail: event.flyer ? "Flyer enviado" : "Falta o flyer",
      ok: !!event.flyer,
      href: `/painel/eventos/${event.id}/aparencia`,
      blocking: true,
    },
    ticketsItem(event, catalog),
    ...quotaItem(event, catalog),
  ]
}

export function ReviewStep({
  organizationId,
  eventId,
}: {
  organizationId: string
  eventId: string
}) {
  const queryClient = useQueryClient()
  const event = useQuery({
    queryKey: ["event", organizationId, eventId],
    queryFn: () => getEvent(organizationId, eventId),
  })
  const catalog = useQuery({
    queryKey: ["tickets", organizationId, eventId],
    queryFn: () => getCatalog(organizationId, eventId),
  })
  const publish = useMutation({
    mutationFn: () => publishEvent(organizationId, eventId),
    onSuccess: (saved) =>
      queryClient.setQueryData(["event", organizationId, eventId], saved),
  })

  if (event.isPending || catalog.isPending) {
    return (
      <div aria-busy="true" className="grid gap-3">
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-16 w-full" />
      </div>
    )
  }
  if (event.isError) return <FormError message={event.error.message} />
  if (catalog.isError) return <FormError message={catalog.error.message} />

  const data = event.data
  const items = checklist(data, catalog.data)
  const ready = items.every((item) => item.ok || !item.blocking)
  const published = data.status === "PUBLISHED"

  return (
    <div className="grid gap-6">
      <ul className="border-t border-foreground">
        {items.map((item) => {
          const body = (
            <>
              <span
                aria-hidden
                className={cn(
                  "flex size-6 shrink-0 items-center justify-center",
                  item.ok
                    ? "bg-primary text-primary-foreground"
                    : item.blocking
                      ? "bg-[#ffb020] text-black"
                      : "border border-input text-muted-foreground",
                )}
              >
                {item.ok ? (
                  <CheckIcon className="size-4" />
                ) : item.blocking ? (
                  "!"
                ) : (
                  <MinusIcon className="size-4" />
                )}
              </span>
              <span className="min-w-0 flex-1">
                <span className="block font-bold">{item.label}</span>
                <span className="block text-sm text-muted-foreground">
                  {item.ok ? "" : item.blocking ? "Pendente: " : ""}
                  {item.detail}
                </span>
              </span>
              {item.href && !published && (
                <span className="text-sm font-bold text-muted-foreground">
                  Editar
                </span>
              )}
            </>
          )
          return (
            <li key={item.label} className="border-b">
              {item.href && !published ? (
                <Link
                  href={item.href}
                  className="flex min-h-16 items-center gap-3 hover:bg-muted"
                >
                  {body}
                </Link>
              ) : (
                <div className="flex min-h-16 items-center gap-3">{body}</div>
              )}
            </li>
          )
        })}
      </ul>

      <FormError message={publish.error?.message ?? null} />

      {published ? (
        <div role="status" className="grid gap-3 border border-primary p-5">
          <p className="font-display text-3xl font-black uppercase">
            {EVENT_STATUS_LABELS.PUBLISHED}
          </p>
          <p className="text-sm text-muted-foreground">
            A página fica em{" "}
            <a
              href={`/e/${data.slug}`}
              target="_blank"
              rel="noopener"
              className="font-bold break-all text-foreground underline underline-offset-4"
            >
              {data.pageUrl.replace(/^https?:\/\//, "")}
            </a>
            . Compartilhe o link para a galera.
          </p>
          <div className="flex flex-wrap gap-2">
            <Button asChild className="h-11">
              <Link href={`/painel/eventos/${data.id}/promoters`}>
                Links de promoter
              </Link>
            </Button>
            <Button asChild variant="outline" className="h-11">
              <Link href="/painel/eventos">Ver meus eventos</Link>
            </Button>
          </div>
        </div>
      ) : (
        <>
          <p className="text-sm text-muted-foreground">
            Ao publicar, a página entra no ar e os links de promoter passam a
            funcionar. Dá para editar depois.
          </p>
          <Button
            type="button"
            size="lg"
            onClick={() => publish.mutate()}
            disabled={!ready || publish.isPending}
            className="h-14 justify-between px-5 font-display text-2xl font-black uppercase"
          >
            {publish.isPending ? "Publicando…" : "Publicar evento"}
            <span aria-hidden>→</span>
          </Button>
          {!ready && (
            <p className="text-sm font-bold text-[#ffb020]">
              Complete os itens pendentes para publicar.
            </p>
          )}
        </>
      )}
    </div>
  )
}
