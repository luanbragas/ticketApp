"use client"

import { useMutation, useQuery } from "@tanstack/react-query"
import { MailWarningIcon, UsersIcon } from "lucide-react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useEffect, useState } from "react"
import { toast } from "sonner"

import { FormError } from "@/components/form/form-error"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"
import { getMe, logout } from "@/lib/api/auth"
import { ApiError } from "@/lib/api/client"
import { acceptInvitation, previewInvitation } from "@/lib/api/organizations"
import { setCurrentOrganization } from "@/lib/current-org"
import { useSessionChanged } from "@/lib/session"

const TOKEN_KEY = "festa:invite"

/**
 * Lê o token do fragmento (/convite#token=...) e o guarda neste navegador, porque a pessoa
 * pode precisar entrar ou criar conta antes de aceitar e voltar para cá sem o fragmento.
 */
function useInvitationToken(): string | null | undefined {
  const [token, setToken] = useState<string | null | undefined>(undefined)
  useEffect(() => {
    const fromHash = new URLSearchParams(window.location.hash.slice(1)).get(
      "token",
    )
    if (fromHash) {
      try {
        localStorage.setItem(TOKEN_KEY, fromHash)
      } catch {
        // Sem armazenamento: o token fica só nesta visita.
      }
      window.history.replaceState(null, "", window.location.pathname)
    }
    let stored: string | null = null
    try {
      stored = localStorage.getItem(TOKEN_KEY)
    } catch {
      stored = null
    }
    // eslint-disable-next-line react-hooks/set-state-in-effect -- fragmento e localStorage só existem no navegador
    setToken(fromHash ?? stored)
  }, [])
  return token
}

export function InvitationView() {
  const router = useRouter()
  const onSessionChanged = useSessionChanged()
  const token = useInvitationToken()

  const preview = useQuery({
    queryKey: ["invitation-preview", token],
    queryFn: () => previewInvitation(token as string),
    enabled: !!token,
  })
  const me = useQuery({
    queryKey: ["me"],
    queryFn: async () => {
      try {
        return await getMe()
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null
        throw error
      }
    },
  })

  const accept = useMutation({
    mutationFn: () => acceptInvitation(token as string),
    onSuccess: (organization) => {
      try {
        localStorage.removeItem(TOKEN_KEY)
      } catch {
        // ignora
      }
      setCurrentOrganization(organization.id)
      toast.success(`Você agora faz parte de ${organization.name}.`)
      router.replace("/painel")
      router.refresh()
    },
  })

  const switchAccount = useMutation({
    mutationFn: logout,
    onSuccess: () => onSessionChanged(),
  })

  if (token === undefined || (token && (preview.isPending || me.isPending))) {
    return (
      <Card>
        <CardHeader>
          <Skeleton className="h-8 w-40" />
          <Skeleton className="h-4 w-full" />
        </CardHeader>
        <CardContent>
          <Skeleton className="h-11 w-full" />
        </CardContent>
      </Card>
    )
  }

  if (!token || preview.isError) {
    return (
      <Card>
        <CardHeader>
          <MailWarningIcon
            className="mb-2 size-8 text-muted-foreground"
            aria-hidden
          />
          <CardTitle className="text-2xl">Convite indisponível</CardTitle>
          <CardDescription>
            {preview.error instanceof ApiError && preview.error.status !== 400
              ? preview.error.message
              : "Não encontramos um convite neste link. Abra de novo o link do e-mail."}
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button asChild variant="outline" className="h-11 w-full">
            <Link href="/painel">Ir para o painel</Link>
          </Button>
        </CardContent>
      </Card>
    )
  }

  const invitation = preview.data
  const user = me.data
  const sameEmail = user?.email === invitation?.email

  return (
    <Card>
      <CardHeader>
        <UsersIcon className="mb-2 size-8 text-primary" aria-hidden />
        <CardTitle className="text-2xl">
          Convite para {invitation?.organizationName}
        </CardTitle>
        <CardDescription>
          Você foi convidado para entrar na equipe como{" "}
          <strong className="text-foreground">{invitation?.roleLabel}</strong>.
          O convite é para{" "}
          <strong className="text-foreground">{invitation?.email}</strong>.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-3">
        <FormError message={accept.error?.message ?? null} />

        {user && sameEmail && (
          <Button
            size="lg"
            className="h-11"
            disabled={accept.isPending}
            onClick={() => accept.mutate()}
          >
            {accept.isPending ? "Aceitando…" : "Aceitar convite"}
          </Button>
        )}

        {user && !sameEmail && (
          <>
            <p className="text-sm text-muted-foreground">
              Você está conectado como{" "}
              <strong className="text-foreground">{user.email}</strong>. Para
              aceitar, entre com o e-mail do convite.
            </p>
            <Button
              size="lg"
              variant="outline"
              className="h-11"
              disabled={switchAccount.isPending}
              onClick={() => switchAccount.mutate()}
            >
              Sair e trocar de conta
            </Button>
          </>
        )}

        {!user && (
          <>
            <Button asChild size="lg" className="h-11">
              <Link href="/entrar?next=/convite">Entrar para aceitar</Link>
            </Button>
            <Button asChild size="lg" variant="outline" className="h-11">
              <Link href="/cadastro?next=/convite">Criar conta</Link>
            </Button>
          </>
        )}
      </CardContent>
    </Card>
  )
}
