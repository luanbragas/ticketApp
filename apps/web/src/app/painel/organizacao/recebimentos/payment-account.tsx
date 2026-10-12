"use client"

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"
import { useRouter, useSearchParams } from "next/navigation"
import { useEffect, useRef } from "react"
import { toast } from "sonner"

import { FormError } from "@/components/form/form-error"
import { Button } from "@/components/ui/button"
import { Skeleton } from "@/components/ui/skeleton"
import {
  connectPaymentAccount,
  disconnectPaymentAccount,
  finishPaymentAccount,
  getPaymentAccount,
} from "@/lib/api/payments"

const when = new Intl.DateTimeFormat("pt-BR", {
  dateStyle: "short",
  timeStyle: "short",
  timeZone: "America/Sao_Paulo",
})

/**
 * Conta do Mercado Pago que recebe as vendas (ADR-003/011). O produtor autoriza no Mercado Pago, que volta para
 * esta página com `code` e `state`; o servidor confere o `state` (uso único) e guarda os tokens criptografados.
 */
export function PaymentAccount({ organizationId }: { organizationId: string }) {
  const queryClient = useQueryClient()
  const router = useRouter()
  const params = useSearchParams()
  const key = ["payment-account", organizationId]
  const account = useQuery({
    queryKey: key,
    queryFn: () => getPaymentAccount(organizationId),
  })
  const connect = useMutation({
    mutationFn: () => connectPaymentAccount(organizationId),
    onSuccess: ({ authorizationUrl }) => {
      window.location.assign(authorizationUrl)
    },
  })
  const finish = useMutation({
    mutationFn: (body: { code: string; state: string }) =>
      finishPaymentAccount(organizationId, body),
    onSuccess: (saved) => {
      queryClient.setQueryData(key, saved)
      toast.success("Conta conectada")
    },
    onSettled: () => router.replace("/painel/organizacao/recebimentos"),
  })
  const disconnect = useMutation({
    mutationFn: () => disconnectPaymentAccount(organizationId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: key }),
  })

  // Volta do Mercado Pago: troca o código uma vez só (o state é de uso único no servidor).
  const code = params.get("code")
  const state = params.get("state")
  const denied = params.get("error")
  const started = useRef(false)
  useEffect(() => {
    if (!code || !state || started.current) return
    started.current = true
    finish.mutate({ code, state })
  }, [code, state, finish])

  if (account.isPending || finish.isPending) {
    return <Skeleton className="h-40 w-full" />
  }
  if (account.isError) return <FormError message={account.error.message} />

  const error =
    finish.error?.message ??
    connect.error?.message ??
    disconnect.error?.message ??
    (denied ? "A autorização foi cancelada no Mercado Pago." : null)
  const data = account.data

  return (
    <div className="grid gap-5">
      <FormError message={error} />
      {data.connected ? (
        <div className="grid gap-3 border border-primary p-5">
          <p className="font-display text-3xl font-black uppercase">
            Conectada
          </p>
          <dl className="grid gap-1 text-sm">
            <div className="flex justify-between gap-3">
              <dt className="text-muted-foreground">Conta Mercado Pago</dt>
              <dd className="font-mono">{data.providerUserId}</dd>
            </div>
            {data.connectedAt && (
              <div className="flex justify-between gap-3">
                <dt className="text-muted-foreground">Desde</dt>
                <dd>{when.format(new Date(data.connectedAt))}</dd>
              </div>
            )}
          </dl>
          <p className="text-sm text-muted-foreground">
            O valor dos ingressos cai direto nessa conta. A taxa de serviço,
            paga pelo comprador, fica com a plataforma.
          </p>
          <div className="flex flex-wrap gap-2">
            <Button
              type="button"
              variant="outline"
              className="h-11"
              disabled={connect.isPending}
              onClick={() => connect.mutate()}
            >
              Trocar de conta
            </Button>
            <Button
              type="button"
              variant="ghost"
              className="h-11"
              disabled={disconnect.isPending}
              onClick={() => {
                if (
                  window.confirm(
                    "Desconectar? Eventos novos não poderão ser publicados até conectar outra conta.",
                  )
                ) {
                  disconnect.mutate()
                }
              }}
            >
              Desconectar
            </Button>
          </div>
        </div>
      ) : (
        <div className="grid gap-3">
          <p>
            Para vender, conecte a conta do Mercado Pago que vai receber o
            dinheiro das vendas. Você autoriza no site do Mercado Pago e volta
            para cá.
          </p>
          <Button
            type="button"
            size="lg"
            disabled={connect.isPending}
            onClick={() => connect.mutate()}
            className="h-14 justify-between px-5 font-display text-2xl font-black uppercase"
          >
            {connect.isPending ? "Abrindo…" : "Conectar Mercado Pago"}
            <span aria-hidden>→</span>
          </Button>
          <p className="text-sm text-muted-foreground">
            A plataforma nunca guarda o dinheiro: ele vai da conta do comprador
            para a sua.
          </p>
        </div>
      )}
    </div>
  )
}
