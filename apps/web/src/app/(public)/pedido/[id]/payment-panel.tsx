"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { CopyIcon } from "lucide-react"
import QRCode from "qrcode"
import { useEffect, useState } from "react"
import { toast } from "sonner"

import { FormError } from "@/components/form/form-error"
import { Button } from "@/components/ui/button"
import {
  getOrderPayment,
  payWithCard,
  payWithPix,
  simulateApproval,
} from "@/lib/api/payments"
import type { OrderPayment } from "@/lib/api/types"
import { formatCents } from "@/lib/money"

/**
 * Pagamento do pedido (PLAN.md M5): gera o PIX, mostra QR e copia e cola e acompanha. O pedido só vira pago
 * quando o servidor confirma pelo webhook (CLAUDE.md regra 4); aqui a tela só espera.
 */
export function PaymentPanel({
  orderId,
  orderKey,
  totalCents,
}: {
  orderId: string
  orderKey: string
  totalCents: number
}) {
  const queryClient = useQueryClient()
  const payment = useQuery({
    queryKey: ["payment", orderId],
    queryFn: () => getOrderPayment(orderId, orderKey),
    // PIX gerado: confere a cada 3 s se o pagamento caiu.
    refetchInterval: (query) =>
      query.state.data?.status === "PENDING" ? 3_000 : false,
  })
  const refresh = () =>
    Promise.all([
      queryClient.invalidateQueries({ queryKey: ["payment", orderId] }),
      queryClient.invalidateQueries({ queryKey: ["order", orderId] }),
    ])
  const pix = useMutation({
    mutationFn: () => payWithPix(orderId, orderKey),
    onSuccess: (created) => {
      queryClient.setQueryData(["payment", orderId], created)
    },
  })
  const testCard = useMutation({
    mutationFn: (token: string) =>
      payWithCard(orderId, orderKey, {
        cardToken: token,
        paymentMethodId: "visa",
      }),
    onSuccess: refresh,
  })
  const simulate = useMutation({
    mutationFn: () => simulateApproval(orderId, orderKey),
    onSuccess: refresh,
  })

  // Pagamento aprovado: o pedido muda logo em seguida (conciliação do webhook).
  useEffect(() => {
    if (payment.data?.status === "APPROVED") {
      queryClient.invalidateQueries({ queryKey: ["order", orderId] })
    }
  }, [payment.data?.status, orderId, queryClient])

  const current = payment.data
  const error =
    pix.error?.message ??
    testCard.error?.message ??
    simulate.error?.message ??
    null

  return (
    <section aria-labelledby="pagamento" className="grid gap-3">
      <h2
        id="pagamento"
        className="border-b border-foreground pb-2 font-display text-3xl leading-none font-black uppercase"
      >
        Pagamento
      </h2>
      <FormError message={error} />

      {current?.method === "PIX" && current.status === "PENDING" ? (
        <PixCode payment={current} />
      ) : current?.status === "APPROVED" ? (
        <p role="status" className="font-bold">
          Pagamento aprovado. Confirmando o pedido…
        </p>
      ) : (
        <>
          {current?.status === "REJECTED" && (
            <p
              role="alert"
              className="border-l-4 border-destructive pl-3 text-sm"
            >
              O cartão foi recusado. Tente outro cartão ou pague com PIX.
            </p>
          )}
          <button
            type="button"
            disabled={pix.isPending}
            onClick={() => pix.mutate()}
            className="flex h-14 items-center justify-between bg-primary px-5 text-primary-foreground disabled:opacity-60"
          >
            <span className="font-display text-2xl font-black uppercase">
              {pix.isPending ? "Gerando PIX…" : "Pagar com PIX"}
            </span>
            <span className="font-extrabold">{formatCents(totalCents)}</span>
          </button>
          <p className="text-sm text-muted-foreground">
            Aprovação na hora. O ingresso chega no seu e-mail.
          </p>
        </>
      )}

      {current?.simulated && current.status === "PENDING" && (
        <TestBox>
          <Button
            type="button"
            variant="outline"
            className="h-11"
            disabled={simulate.isPending}
            onClick={() => simulate.mutate()}
          >
            Simular PIX pago
          </Button>
        </TestBox>
      )}
      {(!current || current.simulated) && current?.status !== "APPROVED" && (
        <SimulatedCard
          visible={!current || current.simulated}
          disabled={testCard.isPending}
          onPay={(token) => testCard.mutate(token)}
        />
      )}
    </section>
  )
}

/** Só aparece com o gateway simulado; o cartão de verdade usa o Card Payment Brick do Mercado Pago. */
function SimulatedCard({
  visible,
  disabled,
  onPay,
}: {
  visible: boolean
  disabled: boolean
  onPay: (token: string) => void
}) {
  if (!visible || process.env.NODE_ENV === "production") return null
  return (
    <TestBox>
      <Button
        type="button"
        variant="outline"
        className="h-11"
        disabled={disabled}
        onClick={() => onPay("fake-ok")}
      >
        Cartão de teste aprovado
      </Button>
      <Button
        type="button"
        variant="ghost"
        className="h-11"
        disabled={disabled}
        onClick={() => onPay("fake-reject")}
      >
        Cartão de teste recusado
      </Button>
    </TestBox>
  )
}

function TestBox({ children }: { children: React.ReactNode }) {
  return (
    <div className="grid gap-2 border border-dashed border-input p-3">
      <p className="text-xs font-bold text-muted-foreground uppercase">
        Ambiente de teste (gateway simulado)
      </p>
      <div className="flex flex-wrap gap-2">{children}</div>
    </div>
  )
}

/** QR Code e copia e cola do PIX. O QR é desenhado aqui a partir do código (nada sai para outro site). */
function PixCode({ payment }: { payment: OrderPayment }) {
  const [svg, setSvg] = useState<string | null>(null)
  useEffect(() => {
    if (!payment.pixQrCode || payment.pixQrBase64) return
    let cancelled = false
    QRCode.toString(payment.pixQrCode, {
      type: "svg",
      margin: 0,
      errorCorrectionLevel: "M",
    })
      .then((s) => !cancelled && setSvg(s))
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [payment.pixQrCode, payment.pixQrBase64])

  const copy = async () => {
    if (!payment.pixQrCode) return
    try {
      await navigator.clipboard.writeText(payment.pixQrCode)
      toast.success("Código PIX copiado")
    } catch {
      toast.error("Não deu para copiar. Segure o código para copiar.")
    }
  }

  return (
    <div className="grid gap-3">
      <p className="text-sm">
        Abra o app do banco, escolha <strong>PIX copia e cola</strong> ou leia o
        QR Code. A confirmação aparece aqui sozinha.
      </p>
      <div
        className="mx-auto w-full max-w-64 bg-white p-4"
        aria-label="QR Code do PIX"
        role="img"
      >
        {payment.pixQrBase64 ? (
          // eslint-disable-next-line @next/next/no-img-element -- QR devolvido pelo gateway em base64
          <img
            src={`data:image/png;base64,${payment.pixQrBase64}`}
            alt=""
            className="w-full"
          />
        ) : svg ? (
          <div dangerouslySetInnerHTML={{ __html: svg }} />
        ) : (
          <div className="aspect-square animate-pulse bg-neutral-200" />
        )}
      </div>
      {payment.pixQrCode && (
        <div className="grid gap-2">
          <p className="truncate border border-input px-3 py-2 font-mono text-xs">
            {payment.pixQrCode}
          </p>
          <Button type="button" className="h-12" onClick={copy}>
            <CopyIcon className="size-4" aria-hidden />
            Copiar código PIX
          </Button>
        </div>
      )}
      <p
        aria-live="polite"
        className="text-center text-sm text-muted-foreground"
      >
        Esperando o pagamento…
      </p>
    </div>
  )
}
