import { PlusIcon } from "lucide-react"
import type { Metadata } from "next"
import Link from "next/link"
import { Suspense } from "react"

import { Skeleton } from "@/components/ui/skeleton"

import { editorOrganization } from "./editor-gate"
import { EventsView } from "./events-view"

export const metadata: Metadata = { title: "Eventos" }

export default function EventsPage() {
  return (
    <Suspense fallback={<ListSkeleton />}>
      <Events />
    </Suspense>
  )
}

async function Events() {
  const { organization, canEdit } = await editorOrganization()
  return (
    <div className="grid gap-6">
      <div className="flex items-end justify-between gap-4">
        <div>
          <p className="text-sm text-muted-foreground">{organization.name}</p>
          <h1 className="mt-1 font-display text-5xl leading-[0.9] font-black uppercase">
            Eventos
          </h1>
        </div>
        {canEdit && (
          <Link
            href="/painel/eventos/novo"
            className="flex h-11 items-center gap-2 bg-primary px-4 text-sm font-extrabold text-primary-foreground"
          >
            <PlusIcon className="size-4" aria-hidden />
            Novo evento
          </Link>
        )}
      </div>
      <EventsView organizationId={organization.id} canEdit={canEdit} />
    </div>
  )
}

function ListSkeleton() {
  return (
    <div aria-busy="true" className="grid gap-6">
      <Skeleton className="h-12 w-48" />
      <Skeleton className="h-11 w-full" />
      <Skeleton className="h-24 w-full" />
      <Skeleton className="h-24 w-full" />
    </div>
  )
}
