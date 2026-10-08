import "server-only"

import { cacheLife, cacheTag } from "next/cache"

import type { PublicEvent } from "@/lib/api/types"

const API_URL =
  process.env.API_INTERNAL_URL ??
  process.env.NEXT_PUBLIC_API_URL ??
  "http://localhost:8080"

/**
 * Evento público pelo slug, em cache de minutos (a API responde com max-age=60). Nulo quando não
 * existe, é rascunho ou foi cancelado.
 */
export async function getPublicEvent(
  slug: string,
): Promise<PublicEvent | null> {
  "use cache"
  cacheLife("minutes")
  cacheTag(`event:${slug}`)
  const response = await fetch(
    `${API_URL}/api/v1/public/events/${encodeURIComponent(slug)}`,
  )
  if (response.status === 404) return null
  if (!response.ok) {
    throw new Error(`API /public/events/${slug} respondeu ${response.status}`)
  }
  return (await response.json()) as PublicEvent
}
