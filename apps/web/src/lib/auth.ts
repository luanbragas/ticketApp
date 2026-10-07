import "server-only"

import { cookies } from "next/headers"
import { redirect } from "next/navigation"
import { cache } from "react"

import type { Me, Organization } from "@/lib/api/types"

/**
 * Camada de acesso a dados no servidor (DAL): confirma a sessão na API repassando
 * o cookie do usuário. O proxy.ts só faz a checagem otimista (cookie presente).
 */

const API_URL =
  process.env.API_INTERNAL_URL ??
  process.env.NEXT_PUBLIC_API_URL ??
  "http://localhost:8080"

export const SESSION_COOKIE = "festa_session"
export const CURRENT_ORG_COOKIE = "festa_org"

async function apiAsUser<T>(path: string): Promise<T | null> {
  const session = (await cookies()).get(SESSION_COOKIE)
  if (!session) return null
  const response = await fetch(`${API_URL}${path}`, {
    headers: { cookie: `${SESSION_COOKIE}=${session.value}` },
    cache: "no-store",
  })
  if (response.status === 401) return null
  if (!response.ok) throw new Error(`API ${path} respondeu ${response.status}`)
  return (await response.json()) as T
}

export const getCurrentUser = cache(() => apiAsUser<Me>("/api/v1/auth/me"))

export const getMyOrganizations = cache(
  async () =>
    (await apiAsUser<{ items: Organization[] }>("/api/v1/orgs"))?.items ?? [],
)

/** Usuário logado ou redireciona para o login. */
export async function requireUser(next = "/painel"): Promise<Me> {
  const user = await getCurrentUser()
  if (!user) redirect(`/entrar?next=${encodeURIComponent(next)}`)
  return user
}

/** Organização escolhida no seletor do painel (cookie), ou a primeira do usuário. */
export async function getCurrentOrganization(): Promise<Organization | null> {
  const organizations = await getMyOrganizations()
  const chosen = (await cookies()).get(CURRENT_ORG_COOKIE)?.value
  return (
    organizations.find((org) => org.id === chosen) ?? organizations[0] ?? null
  )
}
