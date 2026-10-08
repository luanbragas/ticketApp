"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import {
  useMutation,
  useQuery,
  useQueryClient,
  type QueryKey,
} from "@tanstack/react-query"
import { PlusIcon } from "lucide-react"
import Link from "next/link"
import { useState } from "react"
import { useForm } from "react-hook-form"
import { z } from "zod"

import { FormError } from "@/components/form/form-error"
import { TextField } from "@/components/form/text-field"
import { Toggle } from "@/components/form/toggle"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import { getEvent, updateEvent } from "@/lib/api/events"
import {
  closeBatch,
  createBatch,
  createTicketType,
  deleteBatch,
  deleteTicketType,
  getCatalog,
  updateBatch,
  updateTicketType,
} from "@/lib/api/tickets"
import type {
  EventDetail,
  HalfPriceQuota,
  TicketBatch,
  TicketCatalog,
  TicketType,
} from "@/lib/api/types"
import {
  batchSchema,
  emptyBatch,
  fromBatch,
  toBatchBody,
  type BatchInput,
} from "@/lib/schemas/batch"
import { batchDetail, batchStatusLabel, isFinal } from "@/lib/ticket-format"
import { cn } from "@/lib/utils"

/** Só um formulário aberto por vez na tela. */
type Editing =
  | { kind: "new-type"; halfPrice: boolean }
  | { kind: "type"; typeId: string }
  | { kind: "new-batch"; typeId: string }
  | { kind: "batch"; batchId: string }
  | null

type Ctx = {
  organizationId: string
  eventId: string
  /** Troca o catálogo inteiro pelo que a API devolveu (já com a virada). */
  save: (catalog: TicketCatalog) => void
  close: () => void
}

/** Passo 3: tipos de ingresso, fila de lotes e regras de venda (cota de meia e limite por CPF). */
export function TicketsStep({
  organizationId,
  eventId,
}: {
  organizationId: string
  eventId: string
}) {
  const queryClient = useQueryClient()
  const key: QueryKey = ["tickets", organizationId, eventId]
  const catalog = useQuery({
    queryKey: key,
    queryFn: () => getCatalog(organizationId, eventId),
  })
  const event = useQuery({
    queryKey: ["event", organizationId, eventId],
    queryFn: () => getEvent(organizationId, eventId),
  })
  const [editing, setEditing] = useState<Editing>(null)

  if (catalog.isPending || event.isPending) {
    return (
      <div aria-busy="true" className="grid gap-3">
        <Skeleton className="h-12 w-1/2" />
        <Skeleton className="h-20 w-full" />
        <Skeleton className="h-20 w-full" />
      </div>
    )
  }
  if (catalog.isError) return <FormError message={catalog.error.message} />
  if (event.isError) return <FormError message={event.error.message} />

  const ctx: Ctx = {
    organizationId,
    eventId,
    save: (saved) => {
      queryClient.setQueryData(key, saved)
      setEditing(null)
    },
    close: () => setEditing(null),
  }
  const { types, halfPriceQuota } = catalog.data
  const frozen =
    event.data.status === "ENDED" || event.data.status === "CANCELLED"

  return (
    <div className="grid gap-10">
      <p className="max-w-prose text-muted-foreground">
        Cada tipo tem uma fila de lotes. O lote vira sozinho quando esgota ou
        quando chega a data de virada, e o próximo abre na hora.
      </p>

      {types.length === 0 && editing?.kind !== "new-type" && (
        <div className="grid gap-2 border-t border-foreground pt-6">
          <p className="font-display text-5xl leading-[0.9] font-black text-[#6b6b6b] uppercase">
            Sem
            <br />
            ingressos
          </p>
          <p className="max-w-sm">
            Comece pela Pista. Depois, se quiser, crie Camarote, Meia-entrada…
          </p>
        </div>
      )}

      {types.map((type) => (
        <TypeBlock
          key={type.id}
          type={type}
          editing={editing}
          setEditing={frozen ? () => {} : setEditing}
          frozen={frozen}
          ctx={ctx}
        />
      ))}

      {!frozen &&
        (editing?.kind === "new-type" ? (
          <TypeForm
            ctx={ctx}
            halfPrice={editing.halfPrice}
            defaultName={
              editing.halfPrice
                ? "Meia-entrada"
                : types.length === 0
                  ? "Pista"
                  : ""
            }
          />
        ) : (
          <button
            type="button"
            onClick={() => setEditing({ kind: "new-type", halfPrice: false })}
            className="flex h-14 items-center justify-between border border-dashed border-input px-5 font-display text-2xl font-black uppercase hover:border-primary hover:text-primary"
          >
            Tipo de ingresso
            <PlusIcon className="size-5" aria-hidden />
          </button>
        ))}

      <SalesRules
        event={event.data}
        quota={halfPriceQuota}
        hasHalfPriceType={types.some((t) => t.halfPrice)}
        onAddHalfPrice={
          frozen
            ? undefined
            : () => setEditing({ kind: "new-type", halfPrice: true })
        }
        organizationId={organizationId}
      />

      <Link
        href={`/painel/eventos/${eventId}/revisar`}
        className="flex h-14 items-center justify-between bg-primary px-5 font-display text-2xl font-black text-primary-foreground uppercase"
      >
        Continuar
        <span aria-hidden>→</span>
      </Link>
    </div>
  )
}

function TypeBlock({
  type,
  editing,
  setEditing,
  frozen,
  ctx,
}: {
  type: TicketType
  editing: Editing
  setEditing: (e: Editing) => void
  frozen: boolean
  ctx: Ctx
}) {
  const hasSales = type.batches.some((b) => b.sold + b.reserved > 0)
  const remove = useMutation({
    mutationFn: () =>
      deleteTicketType(ctx.organizationId, ctx.eventId, type.id),
    onSuccess: ctx.save,
  })
  const [confirming, setConfirming] = useState(false)

  return (
    <section aria-labelledby={`type-${type.id}`} className="grid gap-2">
      {editing?.kind === "type" && editing.typeId === type.id ? (
        <TypeForm ctx={ctx} type={type} />
      ) : (
        <div className="flex items-end justify-between gap-3 border-b border-foreground pb-2">
          <h2
            id={`type-${type.id}`}
            className="min-w-0 font-display text-3xl leading-none font-black break-words uppercase"
          >
            {type.name}
            {type.halfPrice && (
              <span className="ml-2 inline-block bg-foreground px-1.5 py-0.5 align-middle font-sans text-xs font-extrabold tracking-wide text-background">
                MEIA
              </span>
            )}
          </h2>
          {!frozen && (
            <div className="flex shrink-0 gap-1">
              <Button
                variant="ghost"
                className="h-11"
                onClick={() => setEditing({ kind: "type", typeId: type.id })}
              >
                Renomear
              </Button>
              {!hasSales &&
                (confirming ? (
                  <Button
                    variant="destructive"
                    className="h-11"
                    disabled={remove.isPending}
                    onClick={() => remove.mutate()}
                  >
                    Excluir mesmo
                  </Button>
                ) : (
                  <Button
                    variant="ghost"
                    className="h-11 text-muted-foreground"
                    onClick={() => setConfirming(true)}
                  >
                    Excluir
                  </Button>
                ))}
            </div>
          )}
        </div>
      )}
      {type.description && (
        <p className="text-sm text-muted-foreground">{type.description}</p>
      )}
      <FormError message={remove.error?.message ?? null} />

      <ol className="grid">
        {type.batches.map((batch) => (
          <li key={batch.id} className="border-b">
            {editing?.kind === "batch" && editing.batchId === batch.id ? (
              <BatchForm ctx={ctx} typeId={type.id} batch={batch} />
            ) : (
              <BatchRow
                batch={batch}
                onEdit={
                  frozen || isFinal(batch.status)
                    ? undefined
                    : () => setEditing({ kind: "batch", batchId: batch.id })
                }
              />
            )}
          </li>
        ))}
      </ol>

      {!frozen &&
        (editing?.kind === "new-batch" && editing.typeId === type.id ? (
          <BatchForm
            ctx={ctx}
            typeId={type.id}
            position={type.batches.length}
          />
        ) : (
          <button
            type="button"
            onClick={() => setEditing({ kind: "new-batch", typeId: type.id })}
            className="flex min-h-11 items-center gap-2 text-sm font-extrabold text-primary"
          >
            <PlusIcon className="size-4" aria-hidden />
            Lote {type.batches.length + 1}
          </button>
        ))}
    </section>
  )
}

const PILL: Record<TicketBatch["status"], string> = {
  ON_SALE: "bg-primary text-primary-foreground",
  SCHEDULED: "border border-input text-foreground",
  SOLD_OUT: "bg-foreground text-background",
  CLOSED: "bg-muted text-muted-foreground",
}

function BatchRow({
  batch,
  onEdit,
}: {
  batch: TicketBatch
  onEdit?: () => void
}) {
  const body = (
    <>
      <span className="min-w-0 flex-1">
        <span
          className={cn(
            "block font-bold",
            isFinal(batch.status) && "text-muted-foreground line-through",
          )}
        >
          {batch.name}
          {!batch.visible && (
            <span className="ml-2 text-xs font-normal text-muted-foreground no-underline">
              (escondido)
            </span>
          )}
        </span>
        <span className="block text-sm text-muted-foreground">
          {batchDetail(batch)}
        </span>
      </span>
      <span
        className={cn(
          "shrink-0 px-2 py-0.5 text-xs font-extrabold tracking-wide uppercase",
          PILL[batch.status],
        )}
      >
        {batchStatusLabel(batch)}
      </span>
    </>
  )
  return onEdit ? (
    <button
      type="button"
      onClick={onEdit}
      aria-label={`Editar ${batch.name}`}
      className="flex min-h-16 w-full items-center gap-3 py-2 text-left hover:bg-muted"
    >
      {body}
    </button>
  ) : (
    <div className="flex min-h-16 items-center gap-3 py-2">{body}</div>
  )
}

const typeSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, "Informe o nome do ingresso.")
    .max(60, "Nome muito longo."),
  description: z.string().trim().max(500, "Descrição muito longa."),
})

function TypeForm({
  ctx,
  type,
  halfPrice = false,
  defaultName = "",
}: {
  ctx: Ctx
  type?: TicketType
  halfPrice?: boolean
  defaultName?: string
}) {
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<z.infer<typeof typeSchema>>({
    resolver: zodResolver(typeSchema),
    defaultValues: {
      name: type?.name ?? defaultName,
      description: type?.description ?? "",
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      ctx.save(
        type
          ? await updateTicketType(
              ctx.organizationId,
              ctx.eventId,
              type.id,
              values,
            )
          : await createTicketType(ctx.organizationId, ctx.eventId, {
              ...values,
              halfPrice,
            }),
      )
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Algo deu errado.")
    }
  })

  return (
    <form
      onSubmit={onSubmit}
      noValidate
      className="grid gap-4 border border-primary p-4"
    >
      <p className="font-display text-2xl font-black uppercase">
        {type ? "Renomear" : halfPrice ? "Novo tipo de meia" : "Novo tipo"}
      </p>
      <FormError message={formError} />
      <TextField
        label="Nome"
        placeholder="Pista, Camarote, Backstage…"
        registration={register("name")}
        error={errors.name}
        autoFocus
      />
      <TextField
        label="Descrição (opcional)"
        placeholder="Acesso à pista e ao open bar"
        registration={register("description")}
        error={errors.description}
      />
      {halfPrice && !type && (
        <p className="text-sm text-muted-foreground">
          Estudantes, PCD, jovens de baixa renda e idosos pagam meia com
          documento na entrada. Os lotes deste tipo contam para a cota.
        </p>
      )}
      <FormButtons
        submitting={isSubmitting}
        label={type ? "Salvar" : "Criar tipo"}
        onCancel={ctx.close}
      />
    </form>
  )
}

function BatchForm({
  ctx,
  typeId,
  batch,
  position = 0,
}: {
  ctx: Ctx
  typeId: string
  batch?: TicketBatch
  position?: number
}) {
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<BatchInput>({
    resolver: zodResolver(batchSchema),
    defaultValues: batch ? fromBatch(batch) : emptyBatch(position),
  })
  const close = useMutation({
    mutationFn: () => closeBatch(ctx.organizationId, ctx.eventId, batch!.id),
    onSuccess: ctx.save,
  })
  const remove = useMutation({
    mutationFn: () => deleteBatch(ctx.organizationId, ctx.eventId, batch!.id),
    onSuccess: ctx.save,
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      const body = toBatchBody(values)
      ctx.save(
        batch
          ? await updateBatch(ctx.organizationId, ctx.eventId, batch.id, body)
          : await createBatch(ctx.organizationId, ctx.eventId, typeId, body),
      )
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Algo deu errado.")
    }
  })
  const actionError = close.error?.message ?? remove.error?.message ?? null
  const hasSales = !!batch && batch.sold + batch.reserved > 0

  return (
    <form
      onSubmit={onSubmit}
      noValidate
      className="my-2 grid gap-4 border border-primary p-4"
    >
      <p className="font-display text-2xl font-black uppercase">
        {batch ? `Editar ${batch.name}` : "Novo lote"}
      </p>
      <FormError message={formError ?? actionError} />
      <TextField
        label="Nome"
        registration={register("name")}
        error={errors.name}
      />
      <div className="grid grid-cols-2 gap-3">
        <TextField
          label="Preço (R$)"
          placeholder="30,00"
          inputMode="decimal"
          registration={register("price")}
          error={errors.price}
        />
        <TextField
          label="Quantidade"
          placeholder="100"
          inputMode="numeric"
          registration={register("capacity")}
          error={errors.capacity}
          hint={
            hasSales ? `${batch.sold + batch.reserved} já saíram` : undefined
          }
        />
      </div>
      <div className="grid gap-3 sm:grid-cols-2">
        <TextField
          label="Abre em (opcional)"
          type="datetime-local"
          registration={register("opensAt")}
          error={errors.opensAt}
          hint="Vazio: abre quando o lote anterior virar."
        />
        <TextField
          label="Vira em (opcional)"
          type="datetime-local"
          registration={register("turnsAt")}
          error={errors.turnsAt}
          hint="Vazio: vira só quando esgotar."
        />
      </div>
      <TextField
        label="Máximo por pedido (opcional)"
        placeholder="10"
        inputMode="numeric"
        registration={register("maxPerOrder")}
        error={errors.maxPerOrder}
        className="max-w-48"
      />
      <div className="border-t">
        <Toggle
          label="Mostrar na página do evento"
          hint="Escondido continua na fila; só não aparece para o público."
          registration={register("visible")}
        />
      </div>
      <FormButtons
        submitting={isSubmitting}
        label={batch ? "Salvar lote" : "Criar lote"}
        onCancel={ctx.close}
      />
      {batch && (
        <div className="flex flex-wrap gap-2 border-t pt-4">
          <Button
            type="button"
            variant="outline"
            className="h-11"
            disabled={close.isPending}
            onClick={() => close.mutate()}
          >
            Encerrar lote agora
          </Button>
          {!hasSales && (
            <Button
              type="button"
              variant="ghost"
              className="h-11 text-destructive"
              disabled={remove.isPending}
              onClick={() => remove.mutate()}
            >
              Excluir lote
            </Button>
          )}
          <p className="w-full text-sm text-muted-foreground">
            Lote encerrado não reabre. O próximo da fila abre na hora.
          </p>
        </div>
      )}
    </form>
  )
}

function FormButtons({
  submitting,
  label,
  onCancel,
}: {
  submitting: boolean
  label: string
  onCancel: () => void
}) {
  return (
    <div className="flex gap-2">
      <Button type="submit" className="h-11 flex-1" disabled={submitting}>
        {submitting ? "Salvando…" : label}
      </Button>
      <Button type="button" variant="ghost" className="h-11" onClick={onCancel}>
        Cancelar
      </Button>
    </div>
  )
}

const rulesSchema = z.object({
  halfPriceQuotaPercent: z
    .string()
    .regex(/^\d+$/, "Use de 0 a 100.")
    .refine((v) => Number(v) <= 100, "Use de 0 a 100."),
  maxTicketsPerCpf: z
    .string()
    .refine(
      (v) => v === "" || (/^\d+$/.test(v) && Number(v) >= 1 && Number(v) <= 20),
      "Use de 1 a 20.",
    ),
})

/** Configurações de venda do evento: cota de meia e limite por CPF. */
function SalesRules({
  event,
  quota,
  hasHalfPriceType,
  onAddHalfPrice,
  organizationId,
}: {
  event: EventDetail
  quota: HalfPriceQuota
  hasHalfPriceType: boolean
  onAddHalfPrice?: () => void
  organizationId: string
}) {
  const queryClient = useQueryClient()
  const [formError, setFormError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting, isDirty },
    reset,
  } = useForm<z.infer<typeof rulesSchema>>({
    resolver: zodResolver(rulesSchema),
    defaultValues: {
      halfPriceQuotaPercent: String(event.halfPriceQuotaPercent),
      maxTicketsPerCpf: event.maxTicketsPerCpf
        ? String(event.maxTicketsPerCpf)
        : "",
    },
  })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    setSaved(false)
    try {
      const updated = await updateEvent(organizationId, event.id, {
        halfPriceQuotaPercent: Number(values.halfPriceQuotaPercent),
        // 0 = sem limite (na API, null quer dizer "não mexe").
        maxTicketsPerCpf: values.maxTicketsPerCpf
          ? Number(values.maxTicketsPerCpf)
          : 0,
      })
      queryClient.setQueryData(["event", organizationId, event.id], updated)
      await queryClient.invalidateQueries({
        queryKey: ["tickets", organizationId, event.id],
      })
      reset(values)
      setSaved(true)
    } catch (error) {
      setFormError(error instanceof Error ? error.message : "Algo deu errado.")
    }
  })

  const missing = Math.max(0, quota.minimum - quota.halfPrice)

  return (
    <section aria-labelledby="sales-rules" className="grid gap-4">
      <h2
        id="sales-rules"
        className="border-b border-foreground pb-2 font-display text-3xl leading-none font-black uppercase"
      >
        Regras de venda
      </h2>

      <div className="grid gap-2">
        <p className="font-bold">Meia-entrada</p>
        {quota.total === 0 ? (
          <p className="text-sm text-muted-foreground">
            A conta aparece quando houver lotes.
          </p>
        ) : (
          <>
            <div
              className="h-2 bg-secondary"
              role="meter"
              aria-label="Ingressos de meia em relação ao mínimo"
              aria-valuemin={0}
              aria-valuemax={Math.max(quota.minimum, 1)}
              aria-valuenow={Math.min(quota.halfPrice, quota.minimum)}
            >
              <div
                className={cn(
                  "h-full",
                  quota.met ? "bg-primary" : "bg-[#ffb020]",
                )}
                style={{
                  width: `${quota.minimum === 0 ? 100 : Math.min(100, (quota.halfPrice / quota.minimum) * 100)}%`,
                }}
              />
            </div>
            <p className="text-sm">
              {quota.halfPrice} de {quota.total} ingressos são meia · mínimo de{" "}
              {quota.minimum} ({quota.percent}%)
            </p>
            {!quota.met && (
              <p className="border-l-4 border-[#ffb020] pl-3 text-sm">
                A lei garante meia-entrada para {quota.percent}% dos ingressos.
                Faltam {missing} de meia.
                {!hasHalfPriceType && onAddHalfPrice && (
                  <>
                    {" "}
                    <button
                      type="button"
                      onClick={onAddHalfPrice}
                      className="font-bold text-primary underline underline-offset-4"
                    >
                      Criar tipo de meia
                    </button>
                  </>
                )}
              </p>
            )}
          </>
        )}
      </div>

      <form onSubmit={onSubmit} noValidate className="grid gap-4">
        <FormError message={formError} />
        <div className="grid grid-cols-2 gap-3">
          <TextField
            label="Cota de meia (%)"
            inputMode="numeric"
            registration={register("halfPriceQuotaPercent")}
            error={errors.halfPriceQuotaPercent}
            hint="A lei federal pede 40%."
          />
          <TextField
            label="Limite por CPF"
            placeholder="Sem limite"
            inputMode="numeric"
            registration={register("maxTicketsPerCpf")}
            error={errors.maxTicketsPerCpf}
            hint="Ingressos por pessoa no evento."
          />
        </div>
        <div className="flex items-center gap-3">
          <Button
            type="submit"
            variant="outline"
            className="h-11"
            disabled={isSubmitting || !isDirty}
          >
            {isSubmitting ? "Salvando…" : "Salvar regras"}
          </Button>
          {saved && !isDirty && (
            <p role="status" className="text-sm text-muted-foreground">
              Regras salvas.
            </p>
          )}
        </div>
      </form>
    </section>
  )
}
