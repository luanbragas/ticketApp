"use client"

import { useMutation } from "@tanstack/react-query"
import { LinkIcon, Loader2Icon } from "lucide-react"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useEffect, useRef } from "react"

import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { consumeMagicLink } from "@/lib/api/auth"
import { ApiError } from "@/lib/api/client"
import { safeNext, takeRememberedNext } from "@/lib/forms"
import { useSessionChanged } from "@/lib/session"

/**
 * Destino do link do e-mail: /link-magico#token=...
 * O token vem no fragmento (não chega a servidores nem ao Referer) e sai da barra logo depois de lido.
 */
export default function MagicLinkPage() {
  const router = useRouter()
  const onSessionChanged = useSessionChanged()
  const started = useRef(false)
  const { mutate, error, isError } = useMutation({
    mutationFn: consumeMagicLink,
    onSuccess: () => {
      onSessionChanged()
      router.replace(safeNext(takeRememberedNext()))
      router.refresh()
    },
  })

  useEffect(() => {
    // Em dev o React monta duas vezes; o link só pode ser consumido uma.
    if (started.current) return
    started.current = true
    const token =
      new URLSearchParams(window.location.hash.slice(1)).get("token") ?? ""
    window.history.replaceState(null, "", window.location.pathname)
    mutate(token)
  }, [mutate])

  if (isError) {
    const detail =
      error instanceof ApiError && error.status !== 400
        ? error.message
        : "Este link está incompleto. Abra de novo o link do e-mail ou peça um novo."
    return (
      <Card>
        <CardHeader>
          <LinkIcon className="mb-2 size-8 text-muted-foreground" aria-hidden />
          <CardTitle className="text-2xl">Link inválido</CardTitle>
          <CardDescription>{detail}</CardDescription>
        </CardHeader>
        <CardContent>
          <Button asChild size="lg" className="h-11 w-full">
            <Link href="/entrar">Pedir um novo link</Link>
          </Button>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card aria-busy="true">
      <CardHeader className="items-center text-center">
        <Loader2Icon
          className="mb-2 size-8 animate-spin text-primary"
          aria-hidden
        />
        <CardTitle className="text-2xl">Entrando…</CardTitle>
        <CardDescription>Só um instante.</CardDescription>
      </CardHeader>
    </Card>
  )
}
