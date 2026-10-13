"use client"

import { keepPreviousData, useQuery } from "@tanstack/react-query"
import { MinusIcon, PlusIcon } from "lucide-react"
import Link from "next/link"
import { createContext, use, useMemo, useState } from "react"

import { Skeleton } from "@/components/ui/skeleton"
import { api } from "@/lib/api/client"
import { quote } from "@/lib/api/orders"
import type { PublicAvailability } from "@/lib/api/types"
import { formatCents } from "@/lib/money"
import {
  selectionItems,
  serializeSelection,
  ticketCount,
  type Selection,
} from "@/lib/selection"
import { batchStatusLabel, lowestPrice } from "@/lib/ticket-format"
import { cn } from "@/lib/utils"

const MAX_TICKETS = 20

/**
 * Disponibilidade muda a cada venda: vem do navegador (a página continua em cache) e se atualiza
 * sozinha a cada 30 s. A lista e a barra de comprar dividem a mesma consulta.
 */
function useAvailability(slug: string) {
  return useQuery({
    queryKey: ["availability", slug],
    queryFn: () =>
      api<PublicAvailability>(
        `/api/v1/public/events/${encodeURIComponent(slug)}/availability`,
      ),
    refetchInterval: 30_000,
  })
}

type Shop = {
  slug: string
  selection: Selection
  change: (batchId: string, quantity: number) => void
}

const ShopContext = createContext<Shop | null>(null)

function useShop(): Shop {
  const shop = use(ShopContext)
  if (!shop) throw new Error("useShop fora do ShopProvider")
  return shop
}

/** Guarda a escolha de ingressos entre a lista e a barra de comprar. */
export function ShopProvider({
  slug,
  children,
}: {
  slug: string
  children: React.ReactNode
}) {
  const [selection, setSelection] = useState<Selection>({})
  const shop = useMemo<Shop>(
    () => ({
      slug,
      selection,
      change: (batchId, quantity) =>
        setSelection((current) => ({ ...current, [batchId]: quantity })),
    }),
    [slug, selection],
  )
  return <ShopContext value={shop}>{children}</ShopContext>
}

export function TicketList() {
  const { slug, selection, change } = useShop()
  const availability = useAvailability(slug)
  const total = ticketCount(selection)

  return (
    <section
      aria-labelledby="ingressos-titulo"
      id="ingressos"
      className="mt-6 scroll-mt-4"
    >
      <h2
        id="ingressos-titulo"
        className="font-display text-2xl font-black uppercase"
      >
        Ingressos
      </h2>
      {availability.isPending && (
        <div aria-busy="true" className="mt-2 grid gap-2">
          <Skeleton className="h-14 w-full" />
          <Skeleton className="h-14 w-full" />
        </div>
      )}
      {availability.isError && (
        <p role="alert" className="mt-2 text-sm text-muted-foreground">
          Não deu pra carregar os ingressos agora.{" "}
          <button
            type="button"
            onClick={() => availability.refetch()}
            className="font-bold text-foreground underline underline-offset-4"
          >
            Tentar de novo
          </button>
        </p>
      )}
      {availability.isSuccess && availability.data.types.length === 0 && (
        <p className="mt-2 text-sm text-muted-foreground">
          Os ingressos ainda não foram divulgados.
        </p>
      )}
      {availability.isSuccess &&
        availability.data.types.map((type) => (
          <div key={type.name} className="mt-3">
            <p className="flex items-baseline gap-2 font-bold">
              {type.name}
              {type.halfPrice && (
                <span className="text-xs font-extrabold text-muted-foreground uppercase">
                  meia · com documento
                </span>
              )}
            </p>
            {type.description && (
              <p className="text-sm text-muted-foreground">
                {type.description}
              </p>
            )}
            <ol className="mt-1">
              {type.batches.map((batch) => {
                const live = batch.status === "ON_SALE"
                const buyable = live && batch.availability !== "UNAVAILABLE"
                const gone =
                  batch.status === "SOLD_OUT" || batch.status === "CLOSED"
                const quantity = selection[batch.id] ?? 0
                const label = live
                  ? batch.availability === "LAST_UNITS"
                    ? "Últimos"
                    : batch.availability === "UNAVAILABLE"
                      ? "Tudo reservado"
                      : ""
                  : batchStatusLabel(batch)
                return (
                  <li
                    key={batch.id}
                    className={cn(
                      "flex min-h-14 items-center gap-3 border-b border-secondary py-1.5",
                      live && "border-l-4 border-l-(--accent) pl-3",
                    )}
                  >
                    <span className="min-w-0 flex-1">
                      <span
                        className={cn(
                          "block",
                          gone && "text-muted-foreground line-through",
                        )}
                      >
                        {batch.name}
                      </span>
                      {label && (
                        <span className="block text-xs font-extrabold uppercase">
                          {label}
                        </span>
                      )}
                    </span>
                    <span
                      className={cn(
                        "font-display text-2xl font-extrabold",
                        live ? "text-(--accent)" : "text-muted-foreground",
                        gone && "line-through",
                      )}
                    >
                      {formatCents(batch.priceCents)}
                    </span>
                    {buyable && (
                      <Stepper
                        label={`${type.name} ${batch.name}`}
                        value={quantity}
                        max={Math.min(
                          batch.maxPerOrder,
                          quantity + MAX_TICKETS - total,
                        )}
                        onChange={(next) => change(batch.id, next)}
                      />
                    )}
                  </li>
                )
              })}
            </ol>
          </div>
        ))}
    </section>
  )
}

function Stepper({
  label,
  value,
  max,
  onChange,
}: {
  label: string
  value: number
  max: number
  onChange: (value: number) => void
}) {
  return (
    <span
      role="group"
      aria-label={`Quantidade de ${label}`}
      className="flex shrink-0 items-center"
    >
      <button
        type="button"
        aria-label="Menos um"
        disabled={value === 0}
        onClick={() => onChange(value - 1)}
        className="flex size-11 items-center justify-center border border-input disabled:opacity-30"
      >
        <MinusIcon className="size-4" aria-hidden />
      </button>
      <span
        aria-live="polite"
        className="w-8 text-center font-display text-2xl font-extrabold"
      >
        {value}
      </span>
      <button
        type="button"
        aria-label="Mais um"
        disabled={value >= max}
        onClick={() => onChange(value + 1)}
        className="flex size-11 items-center justify-center bg-(--accent) text-(--on-accent) disabled:opacity-30"
      >
        <PlusIcon className="size-4" aria-hidden />
      </button>
    </span>
  )
}

/**
 * Barra fixa. Sem escolha: "a partir de" e leva à lista. Com escolha: total calculado pela API (preço +
 * taxa de serviço) e segue para o checkout.
 */
export function BuyBar({ ended }: { ended: boolean }) {
  const { slug, selection } = useShop()
  const availability = useAvailability(slug)
  const items = selectionItems(selection)
  const count = ticketCount(selection)
  const priced = useQuery({
    queryKey: ["quote", slug, serializeSelection(selection)],
    queryFn: () => quote(slug, items),
    enabled: count > 0,
    placeholderData: keepPreviousData,
    retry: false,
  })
  const from = availability.data ? lowestPrice(availability.data) : null
  const soldOut =
    availability.isSuccess &&
    from === null &&
    availability.data.types.length > 0 &&
    availability.data.types.every((t) =>
      t.batches.every((b) => b.status === "SOLD_OUT" || b.status === "CLOSED"),
    )

  if (ended || soldOut) {
    return (
      <div className="flex h-14 items-center justify-between bg-secondary px-5">
        <span className="font-display text-2xl font-black uppercase">
          {ended ? "Encerrado" : "Esgotado"}
        </span>
        <span className="text-sm font-extrabold">
          {ended ? "obrigado por ir!" : "fique de olho no próximo"}
        </span>
      </div>
    )
  }
  if (count > 0) {
    return (
      <div className="grid gap-1">
        {priced.isError && (
          <p role="alert" className="text-sm text-destructive">
            {priced.error.message}
          </p>
        )}
        <Link
          href={`/e/${slug}/checkout?i=${serializeSelection(selection)}`}
          aria-disabled={priced.isError || undefined}
          className={cn(
            "flex h-14 items-center justify-between bg-(--accent) px-5 text-(--on-accent)",
            priced.isError && "pointer-events-none opacity-50",
          )}
        >
          <span className="font-display text-2xl font-black uppercase">
            Continuar
          </span>
          <span className="text-right text-sm leading-tight font-extrabold">
            {count} {count === 1 ? "ingresso" : "ingressos"}
            <br />
            {priced.data ? formatCents(priced.data.totalCents) : "…"}
          </span>
        </Link>
      </div>
    )
  }
  return (
    <a
      href="#ingressos"
      className="flex h-14 items-center justify-between bg-(--accent) px-5 text-(--on-accent)"
    >
      <span className="font-display text-2xl font-black uppercase">
        Comprar
      </span>
      <span className="text-sm font-extrabold">
        {from !== null
          ? `a partir de ${formatCents(from)}`
          : "ingressos em breve"}
      </span>
    </a>
  )
}
