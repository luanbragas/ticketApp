"use client"

import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
} from "@tanstack/react-query"
import { SearchIcon } from "lucide-react"
import { useEffect, useId, useState } from "react"
import { toast } from "sonner"

import { FormError } from "@/components/form/form-error"
import { Button } from "@/components/ui/button"
import { Input } from "@/components/ui/input"
import { Skeleton } from "@/components/ui/skeleton"
import { getParticipants, undoCheckin } from "@/lib/api/checkin"
import { refundOrder } from "@/lib/api/payments"
import type { Participant, TicketStatus } from "@/lib/api/types"
import { formatCents } from "@/lib/money"
import { cn } from "@/lib/utils"

const FILTERS: { value: TicketStatus | undefined; label: string }[] = [
  { value: undefined, label: "Todos" },
  { value: "CHECKED_IN", label: "Entraram" },
  { value: "VALID", label: "Não entraram" },
]

const TIME = new Intl.DateTimeFormat("pt-BR", {
  hour: "2-digit",
  minute: "2-digit",
  timeZone: "America/Sao_Paulo",
})

/**
 * Participantes (PLAN.md M8): busca por nome ou CPF completo, filtro por entrada, desfazer check-in e (M9)
 * reembolsar o pedido de um ingresso.
 */
export function ParticipantsView({
  organizationId,
  eventId,
  canUndo,
}: {
  organizationId: string
  eventId: string
  canUndo: boolean
}) {
  const searchId = useId()
  const [typed, setTyped] = useState("")
  const [query, setQuery] = useState("")
  const [status, setStatus] = useState<TicketStatus | undefined>(undefined)

  // Espera a pessoa parar de digitar antes de buscar.
  useEffect(() => {
    const timer = setTimeout(() => setQuery(typed.trim()), 300)
    return () => clearTimeout(timer)
  }, [typed])

  const key = ["participants", organizationId, eventId, query, status]
  const participants = useQuery({
    queryKey: key,
    queryFn: () =>
      getParticipants(organizationId, eventId, {
        q: query,
        status,
        limit: 100,
      }),
    placeholderData: keepPreviousData,
    refetchInterval: 30_000,
  })

  return (
    <div className="grid gap-6">
      <div>
        <h1 className="font-display text-5xl leading-[0.9] font-black uppercase">
          Participantes
        </h1>
        {participants.data && (
          <p className="mt-2 text-sm text-muted-foreground">
            {participants.data.checkedIn} de {participants.data.issued} já
            entraram
          </p>
        )}
      </div>

      {participants.data && participants.data.duplicates > 0 && (
        <p className="border-l-4 border-[#ffb020] pl-3 text-sm">
          {participants.data.duplicates}{" "}
          {participants.data.duplicates === 1
            ? "leitura repetida"
            : "leituras repetidas"}{" "}
          na portaria (o mesmo ingresso lido em mais de um aparelho). Vale a
          primeira leitura.
        </p>
      )}

      <div className="grid gap-3">
        <label htmlFor={searchId} className="sr-only">
          Buscar por nome ou CPF
        </label>
        <div className="relative">
          <SearchIcon
            className="absolute top-1/2 left-0 size-4 -translate-y-1/2 text-muted-foreground"
            aria-hidden
          />
          <Input
            id={searchId}
            type="search"
            value={typed}
            onChange={(e) => setTyped(e.target.value)}
            placeholder="Nome ou CPF completo"
            className="h-11 pl-6"
          />
        </div>
        <div role="group" aria-label="Filtrar" className="flex gap-5 border-b">
          {FILTERS.map((f) => (
            <button
              key={f.label}
              type="button"
              aria-pressed={f.value === status}
              onClick={() => setStatus(f.value)}
              className={cn(
                "-mb-px min-h-11 border-b-2 text-sm font-extrabold",
                f.value === status
                  ? "border-primary text-foreground"
                  : "border-transparent text-muted-foreground hover:text-foreground",
              )}
            >
              {f.label}
            </button>
          ))}
        </div>
      </div>

      {participants.isPending && (
        <div aria-busy="true" className="grid gap-2">
          <Skeleton className="h-16 w-full" />
          <Skeleton className="h-16 w-full" />
        </div>
      )}
      {participants.isError && (
        <FormError message={participants.error.message} />
      )}
      {participants.data &&
        (participants.data.items.length === 0 ? (
          <p className="text-muted-foreground">
            {query
              ? "Ninguém com esse nome ou CPF."
              : "Nenhum ingresso emitido ainda."}
          </p>
        ) : (
          <ul
            className={cn("border-t", participants.isFetching && "opacity-70")}
          >
            {participants.data.items.map((p) => (
              <Row
                key={p.ticket.ticketId}
                participant={p}
                canUndo={canUndo}
                organizationId={organizationId}
                eventId={eventId}
              />
            ))}
          </ul>
        ))}
      {participants.data?.hasMore && (
        <p className="text-sm text-muted-foreground">
          Mostrando os 100 primeiros. Busque pelo nome para achar alguém.
        </p>
      )}
    </div>
  )
}

function Row({
  participant,
  canUndo,
  organizationId,
  eventId,
}: {
  participant: Participant
  canUndo: boolean
  organizationId: string
  eventId: string
}) {
  const queryClient = useQueryClient()
  const [confirming, setConfirming] = useState(false)
  const undo = useMutation({
    mutationFn: () => undoCheckin(organizationId, participant.checkinId!),
    onSuccess: () => {
      setConfirming(false)
      return queryClient.invalidateQueries({
        queryKey: ["participants", organizationId, eventId],
      })
    },
  })
  const { ticket } = participant
  const inside = ticket.status === "CHECKED_IN"

  return (
    <li className="grid gap-1 border-b py-3">
      <div className="flex items-baseline justify-between gap-3">
        <p className="min-w-0 truncate font-bold">{ticket.holderName}</p>
        <span
          className={cn(
            "shrink-0 px-2 py-0.5 text-xs font-extrabold tracking-wide uppercase",
            inside
              ? "bg-primary text-primary-foreground"
              : ticket.status === "VALID"
                ? "border border-input"
                : "bg-muted text-muted-foreground",
          )}
        >
          {inside
            ? `Entrou ${participant.checkedInAt ? TIME.format(new Date(participant.checkedInAt)) : ""}`
            : ticket.status === "VALID"
              ? "Não entrou"
              : ticket.status === "CANCELLED"
                ? "Cancelado"
                : "Inválido"}
        </span>
      </div>
      <p className="text-sm text-muted-foreground">
        {ticket.typeName} · {ticket.batchName}
        {ticket.halfPrice ? " · meia (conferir documento)" : ""}
        {participant.holderCpf ? ` · CPF ${participant.holderCpf}` : ""}
      </p>
      {canUndo && inside && participant.checkinId && (
        <div className="flex items-center gap-2">
          {confirming ? (
            <>
              <Button
                type="button"
                variant="destructive"
                className="h-10"
                disabled={undo.isPending}
                onClick={() => undo.mutate()}
              >
                Desfazer entrada
              </Button>
              <Button
                type="button"
                variant="ghost"
                className="h-10"
                onClick={() => setConfirming(false)}
              >
                Cancelar
              </Button>
            </>
          ) : (
            <Button
              type="button"
              variant="ghost"
              className="h-10 px-0 text-muted-foreground"
              onClick={() => setConfirming(true)}
            >
              Desfazer check-in
            </Button>
          )}
        </div>
      )}
      <FormError message={undo.error?.message ?? null} />
      {canUndo && ticket.status === "VALID" && participant.orderId && (
        <Refund
          organizationId={organizationId}
          eventId={eventId}
          orderId={participant.orderId}
        />
      )}
    </li>
  )
}

/** Reembolso do pedido inteiro: pede o motivo e confirma, porque não tem volta. */
function Refund({
  organizationId,
  eventId,
  orderId,
}: {
  organizationId: string
  eventId: string
  orderId: string
}) {
  const queryClient = useQueryClient()
  const reasonId = useId()
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState("")
  const refund = useMutation({
    mutationFn: () => refundOrder(organizationId, orderId, reason.trim()),
    onSuccess: ({ amountCents }) => {
      setOpen(false)
      toast.success(`Reembolso de ${formatCents(amountCents)} feito`)
      // Os ingressos são cancelados logo em seguida (fila de eventos): busca de novo daqui a pouco.
      setTimeout(
        () =>
          queryClient.invalidateQueries({
            queryKey: ["participants", organizationId, eventId],
          }),
        3_000,
      )
    },
  })

  if (!open) {
    return (
      <Button
        type="button"
        variant="ghost"
        className="h-10 w-fit px-0 text-muted-foreground"
        onClick={() => setOpen(true)}
      >
        Reembolsar pedido
      </Button>
    )
  }
  return (
    <form
      className="grid gap-2 border border-input p-3"
      onSubmit={(e) => {
        e.preventDefault()
        refund.mutate()
      }}
    >
      <p className="text-sm">
        Devolve o valor pago (ingressos e taxa) e cancela{" "}
        <strong>todos os ingressos desse pedido</strong>. Não dá para desfazer.
      </p>
      <label htmlFor={reasonId} className="text-sm font-bold">
        Motivo
      </label>
      <textarea
        id={reasonId}
        rows={2}
        className="min-h-16 w-full border border-input bg-transparent px-3 py-2 text-base outline-none focus-visible:border-ring focus-visible:ring-[3px] focus-visible:ring-ring/50 md:text-sm"
        value={reason}
        maxLength={500}
        required
        onChange={(e) => setReason(e.target.value)}
        placeholder="Ex.: comprou duas vezes por engano"
      />
      <FormError message={refund.error?.message ?? null} />
      <div className="flex gap-2">
        <Button
          type="submit"
          variant="destructive"
          className="h-10"
          disabled={refund.isPending || reason.trim() === ""}
        >
          {refund.isPending ? "Reembolsando…" : "Reembolsar"}
        </Button>
        <Button
          type="button"
          variant="ghost"
          className="h-10"
          onClick={() => setOpen(false)}
        >
          Cancelar
        </Button>
      </div>
    </form>
  )
}
