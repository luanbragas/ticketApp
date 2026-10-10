import type { Metadata } from "next"
import Link from "next/link"
import { Suspense } from "react"

import { Skeleton } from "@/components/ui/skeleton"

import { editorOrganization, NoPermission } from "../../editor-gate"
import { PromotersView } from "./promoters-view"

export const metadata: Metadata = { title: "Promoters" }

/** Quem vê: dono, admin e gerente (tudo) e promoter (só o próprio link). */
const VIEWERS = ["OWNER", "ADMIN", "MANAGER", "PROMOTER"]

export default function Page({
  params,
}: PageProps<"/painel/eventos/[id]/promoters">) {
  return (
    <Suspense fallback={<Skeleton className="h-64 w-full" />}>
      <Promoters params={params} />
    </Suspense>
  )
}

async function Promoters({ params }: { params: Promise<{ id: string }> }) {
  const [{ id }, { organization }] = await Promise.all([
    params,
    editorOrganization(),
  ])
  return (
    <div className="grid gap-6">
      <Link
        href="/painel/eventos"
        className="w-fit text-sm font-bold text-muted-foreground hover:text-foreground"
      >
        ← Eventos
      </Link>
      {VIEWERS.includes(organization.role) ? (
        <PromotersView organizationId={organization.id} eventId={id} />
      ) : (
        <NoPermission message="Promoters é para dono, administradores, gerentes e os próprios promoters." />
      )}
    </div>
  )
}
