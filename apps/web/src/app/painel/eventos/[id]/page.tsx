import type { Metadata } from "next"
import { redirect } from "next/navigation"
import { Suspense } from "react"

import { Skeleton } from "@/components/ui/skeleton"

import { editorOrganization } from "../editor-gate"
import { EventHome } from "./event-home"

export const metadata: Metadata = { title: "Evento" }

export default function Page({ params }: PageProps<"/painel/eventos/[id]">) {
  return (
    <Suspense fallback={<Skeleton className="h-96 w-full" />}>
      <Home params={params} />
    </Suspense>
  )
}

/** Gerência vê o painel do evento; promoter vai para o próprio link e a portaria para o check-in. */
async function Home({ params }: { params: Promise<{ id: string }> }) {
  const [{ id }, { organization }] = await Promise.all([
    params,
    editorOrganization(),
  ])
  if (organization.role === "PROMOTER")
    redirect(`/painel/eventos/${id}/promoters`)
  if (organization.role === "CHECKIN_OPERATOR") redirect(`/checkin/${id}`)
  return <EventHome organizationId={organization.id} eventId={id} />
}
