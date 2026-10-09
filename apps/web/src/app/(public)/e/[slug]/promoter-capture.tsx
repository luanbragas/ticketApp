"use client"

import { useSearchParams } from "next/navigation"
import { useEffect } from "react"

import { promoterCookie } from "@/lib/promoter-cookie"

/** Guarda o promoter do link (?p=) por 7 dias; último clique vence (ADR-009). Não renderiza nada. */
export function PromoterCapture({ slug }: { slug: string }) {
  const code = useSearchParams().get("p")
  useEffect(() => {
    if (!code) return
    const cookie = promoterCookie(slug, code)
    if (cookie) document.cookie = cookie
  }, [slug, code])
  return null
}
