"use client"

import { ShareIcon } from "lucide-react"
import { useEffect, useState } from "react"
import { toast } from "sonner"

import { countdownLabel } from "@/lib/event-format"

/** "Em 38 dias" calculado no navegador: a página fica em cache e a data de hoje muda. */
export function Countdown({ startsAt }: { startsAt: string }) {
  const [label, setLabel] = useState<string | null>(null)
  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect -- depende do relógio do aparelho, só existe no cliente
    setLabel(countdownLabel(startsAt, new Date()))
  }, [startsAt])
  if (!label) return <span aria-hidden className="block h-7" />
  return (
    <span className="inline-block bg-(--accent) px-3 py-1 text-xs font-extrabold tracking-wide text-(--on-accent) uppercase">
      {label}
    </span>
  )
}

/** Compartilhar: menu nativo do celular ou, no computador, copia o link. */
export function ShareButton({ title }: { title: string }) {
  const share = async () => {
    const url = window.location.href
    if (navigator.share) {
      try {
        await navigator.share({ title, url })
      } catch {
        // Usuário fechou o menu.
      }
      return
    }
    await navigator.clipboard.writeText(url)
    toast.success("Link copiado.")
  }
  return (
    <button
      type="button"
      onClick={share}
      aria-label="Compartilhar"
      className="flex size-11 items-center justify-center"
    >
      <ShareIcon className="size-5" aria-hidden />
    </button>
  )
}
