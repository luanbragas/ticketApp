"use client"

import { useQuery } from "@tanstack/react-query"
import Link from "next/link"
import { useEffect, useState } from "react"

import { FormError } from "@/components/form/form-error"
import { Skeleton } from "@/components/ui/skeleton"
import { getOrder } from "@/lib/api/orders"
import { HALF_PRICE_REASONS, type PublicOrder } from "@/lib/api/types"
import { formatCents } from "@/lib/money"
import { orderKey } from "@/lib/order-keys"

/** Pedido recém-criado: reserva com contador. O pagamento (PIX e cartão) entra aqui no M5. */
export function OrderView({ orderId }: { orderId: string }) {
  const [key, setKey] = useState<string | null | undefined>(undefined)
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- a chave só existe no navegador
    setKey(orderKey(orderId))
  }, [orderId])
  const order = useQuery({
    queryKey: ["order", orderId],
    queryFn: () => getOrder(orderId, key!),
    enabled: !!key,
    refetchInterval: (query) =>
      query.state.data?.status === "PENDING_PAYMENT" ? 10_000 : false,
  })

  if (key === undefined || (key && order.isPending)) {
    return <Skeleton className="mt-6 h-64 w-full" />
  }
  if (key === null) {
    return (
      <div className="mt-6 grid gap-2">
        <h1 className="font-display text-4xl font-black uppercase">
          Pedido em outro aparelho
        </h1>
        <p className="text-muted-foreground">
          Abra este link no mesmo celular ou navegador em que você fez o pedido.
          Quando o pagamento for confirmado, os ingressos também chegam no seu
          e-mail.
        </p>
      </div>
    )
  }
  if (order.isError) return <FormError message={order.error.message} />
  return <Details order={order.data!} />
}

function Details({ order }: { order: PublicOrder }) {
  const remaining = useCountdown(order.expiresAt)
  const expired =
    order.status === "EXPIRED" ||
    (order.status === "PENDING_PAYMENT" && remaining === 0)

  return (
    <div className="mt-4 grid gap-8">
      <div>
        <p className="text-sm text-muted-foreground">{order.event.name}</p>
        {expired ? (
          <>
            <h1 className="mt-1 font-display text-5xl leading-[0.9] font-black uppercase">
              Reserva expirou
            </h1>
            <p className="mt-2 text-muted-foreground">
              Os ingressos voltaram para a venda. Se ainda tiver, dá para
              escolher de novo.
            </p>
            <Link
              href={`/e/${order.event.slug}#ingressos`}
              className="mt-4 flex h-14 items-center justify-between bg-primary px-5 font-display text-2xl font-black text-primary-foreground uppercase"
            >
              Escolher de novo
              <span aria-hidden>→</span>
            </Link>
          </>
        ) : order.status === "PENDING_PAYMENT" ? (
          <>
            <h1 className="mt-1 font-display text-5xl leading-[0.9] font-black uppercase">
              Reservado
            </h1>
            <p className="mt-3 flex items-baseline gap-3">
              <span
                className="font-display text-6xl font-black text-primary tabular-nums"
                role="timer"
                aria-label="Tempo para pagar"
              >
                {clock(remaining)}
              </span>
              <span className="text-sm text-muted-foreground">
                para pagar antes de os ingressos voltarem para a venda
              </span>
            </p>
          </>
        ) : order.status === "PAID" ? (
          <>
            <h1 className="mt-1 font-display text-5xl leading-[0.9] font-black uppercase">
              Pago
            </h1>
            <p className="mt-2 text-muted-foreground">
              Os ingressos foram para {order.buyer.email}, com o QR Code de cada
              titular.
            </p>
            <Link
              href="/meus-ingressos"
              className="mt-4 flex h-14 items-center justify-between bg-primary px-5 font-display text-2xl font-black text-primary-foreground uppercase"
            >
              Meus ingressos
              <span aria-hidden>→</span>
            </Link>
          </>
        ) : (
          <h1 className="mt-1 font-display text-5xl leading-[0.9] font-black uppercase">
            Pedido encerrado
          </h1>
        )}
      </div>

      <section aria-labelledby="itens" className="grid gap-2">
        <h2
          id="itens"
          className="border-b border-foreground pb-2 font-display text-3xl leading-none font-black uppercase"
        >
          Ingressos
        </h2>
        <ol>
          {order.items.map((item, index) => (
            <li
              key={index}
              className="flex items-baseline justify-between gap-3 border-b border-secondary py-2"
            >
              <span className="min-w-0">
                <span className="block font-bold">{item.holderName}</span>
                <span className="block text-sm text-muted-foreground">
                  {item.typeName} · {item.batchName} · CPF {item.holderCpf}
                  {item.halfPriceReason &&
                    ` · meia: ${HALF_PRICE_REASONS[item.halfPriceReason]}`}
                </span>
              </span>
              <span className="shrink-0">
                {formatCents(item.unitPriceCents)}
              </span>
            </li>
          ))}
        </ol>
        <dl className="grid gap-1 text-sm">
          <div className="flex justify-between text-muted-foreground">
            <dt>Taxa de serviço</dt>
            <dd>{formatCents(order.feeCents)}</dd>
          </div>
          <div className="flex items-baseline justify-between border-t pt-2">
            <dt className="font-bold">Total</dt>
            <dd className="font-display text-3xl font-black">
              {formatCents(order.totalCents)}
            </dd>
          </div>
        </dl>
      </section>

      {order.status === "PENDING_PAYMENT" && !expired && (
        <section aria-labelledby="pagamento" className="grid gap-2">
          <h2
            id="pagamento"
            className="border-b border-foreground pb-2 font-display text-3xl leading-none font-black uppercase"
          >
            Pagamento
          </h2>
          <div
            aria-disabled="true"
            className="flex h-14 items-center justify-between bg-secondary px-5 text-muted-foreground"
          >
            <span className="font-display text-2xl font-black uppercase">
              Pagar com PIX
            </span>
            <span className="text-sm font-extrabold">em breve</span>
          </div>
          <p className="text-sm text-muted-foreground">
            Pedido no nome de {order.buyer.name} ({order.buyer.email}).
          </p>
        </section>
      )}
    </div>
  )
}

/** Segundos até o instante, atualizado a cada segundo (relógio do aparelho). */
function useCountdown(iso: string): number {
  const target = new Date(iso).getTime()
  const [now, setNow] = useState(() => Date.now())
  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(timer)
  }, [])
  return Math.max(0, Math.ceil((target - now) / 1000))
}

function clock(seconds: number): string {
  const m = Math.floor(seconds / 60)
  const s = seconds % 60
  return `${String(m).padStart(2, "0")}:${String(s).padStart(2, "0")}`
}
