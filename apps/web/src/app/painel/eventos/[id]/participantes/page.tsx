import type { Metadata } from "next"
import Link from "next/link"
import { Suspense } from "react"

import { Skeleton } from "@/components/ui/skeleton"

import { editorOrganization, NoPermission } from "../../editor-gate"
import { ParticipantsView } from "./participants-view"

export const metadata: Metadata = { title: "Participantes" }

const VIEWERS = ["OWNER", "ADMIN", "MANAGER", "CHECKIN_OPERATOR"]

export default function Page({
  params,
}: PageProps<"/painel/eventos/[id]/participantes">) {
  return (
    <Suspense fallback={<Skeleton className="h-64 w-full" />}>
      <Participants params={params} />
    </Suspense>
  )
}

async function Participants({ params }: { params: Promise<{ id: string }> }) {
  const [{ id }, { organization }] = await Promise.all([
    params,
    editorOrganization(),
  ])
  return (
    <div className="grid gap-6">
      <Link
        href={`/painel/eventos/${id}`}
        className="w-fit text-sm font-bold text-muted-foreground hover:text-foreground"
      >
        ← Evento
      </Link>
      {VIEWERS.includes(organization.role) ? (
        <ParticipantsView
          organizationId={organization.id}
          eventId={id}
          canUndo={
            organization.role === "OWNER" || organization.role === "ADMIN"
          }
        />
      ) : (
        <NoPermission message="Participantes é para dono, administradores, gerentes e portaria." />
      )}
    </div>
  )
}
