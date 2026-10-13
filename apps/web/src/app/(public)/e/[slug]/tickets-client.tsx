"use client"

import { useQuery } from "@tanstack/react-query"

import { Skeleton } from "@/components/ui/skeleton"
import { api } from "@/lib/api/client"
import type { PublicAvailability } from "@/lib/api/types"
import { formatCents } from "@/lib/money"
import { batchStatusLabel, lowestPrice } from "@/lib/ticket-format"
import { cn } from "@/lib/utils"

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

export function TicketList({ slug }: { slug: string }) {
  const availability = useAvailability(slug)

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
                const gone =
                  batch.status === "SOLD_OUT" || batch.status === "CLOSED"
                return (
                  <li
                    key={batch.id}
                    className={cn(
                      "flex min-h-12 items-center gap-3 border-b border-secondary py-2",
                      live && "border-l-4 border-l-(--accent) pl-3",
                    )}
                  >
                    <span
                      className={cn(
                        "min-w-0 flex-1",
                        gone && "text-muted-foreground line-through",
                      )}
                    >
                      {batch.name}
                    </span>
                    <span className="text-xs font-extrabold uppercase">
                      {live
                        ? batch.availability === "LAST_UNITS"
                          ? "Últimos"
                          : batch.availability === "UNAVAILABLE"
                            ? "Tudo reservado"
                            : ""
                        : batchStatusLabel(batch)}
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
                  </li>
                )
              })}
            </ol>
          </div>
        ))}
    </section>
  )
}

/** Barra fixa: leva à lista com o menor preço à venda. A compra em si chega com os pedidos (M4). */
export function BuyBar({ slug, ended }: { slug: string; ended: boolean }) {
  const availability = useAvailability(slug)
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
