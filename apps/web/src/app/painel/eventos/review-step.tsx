"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { CheckIcon, MinusIcon } from "lucide-react"
import Link from "next/link"

import { FormError } from "@/components/form/form-error"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { getEvent, publishEvent } from "@/lib/api/events"
import { EVENT_STATUS_LABELS, type EventDetail } from "@/lib/api/types"
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

function checklist(event: EventDetail): Item[] {
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
    {
      label: "Ingressos",
      detail: "Tipos e lotes chegam na próxima etapa do produto",
      ok: false,
      blocking: false,
    },
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
  const publish = useMutation({
    mutationFn: () => publishEvent(organizationId, eventId),
    onSuccess: (saved) =>
      queryClient.setQueryData(["event", organizationId, eventId], saved),
  })

  if (event.isPending) {
    return (
      <div aria-busy="true" className="grid gap-3">
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-16 w-full" />
        <Skeleton className="h-16 w-full" />
      </div>
    )
  }
  if (event.isError) return <FormError message={event.error.message} />

  const data = event.data
  const items = checklist(data)
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
            <span className="font-bold break-all text-foreground">
              {data.pageUrl.replace(/^https?:\/\//, "")}
            </span>
            . A página pública chega na próxima tarefa do plano.
          </p>
          <Button asChild variant="outline" className="h-11 w-fit">
            <Link href="/painel">Voltar ao painel</Link>
          </Button>
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
