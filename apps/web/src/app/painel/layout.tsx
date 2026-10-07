import { Suspense } from "react"

import { Skeleton } from "@/components/ui/skeleton"
import {
  getCurrentOrganization,
  getMyOrganizations,
  requireUser,
} from "@/lib/auth"

import { PanelShell } from "./panel-shell"

/**
 * A sessão é lida dentro do Suspense (Cache Components): o esqueleto do painel aparece
 * na hora e o conteúdo do usuário chega em streaming.
 */
export default function PanelLayout({ children }: LayoutProps<"/painel">) {
  return (
    <Suspense fallback={<PanelSkeleton />}>
      <PanelFrame>{children}</PanelFrame>
    </Suspense>
  )
}

async function PanelFrame({ children }: { children: React.ReactNode }) {
  const user = await requireUser()
  const [organizations, current] = await Promise.all([
    getMyOrganizations(),
    getCurrentOrganization(),
  ])

  return (
    <PanelShell user={user} organizations={organizations} current={current}>
      {children}
    </PanelShell>
  )
}

function PanelSkeleton() {
  return (
    <div aria-busy="true" className="flex min-h-dvh flex-1 bg-muted/40">
      <div className="hidden w-64 shrink-0 border-r bg-background p-5 md:block">
        <Skeleton className="h-6 w-20" />
        <Skeleton className="mt-6 h-11 w-full" />
        <Skeleton className="mt-4 h-10 w-full" />
        <Skeleton className="mt-1 h-10 w-full" />
      </div>
      <div className="flex-1">
        <div className="h-14 border-b bg-background md:hidden" />
        <div className="mx-auto grid max-w-5xl gap-4 px-4 pt-6 md:px-8 md:pt-10">
          <Skeleton className="h-4 w-40" />
          <Skeleton className="h-8 w-56" />
          <Skeleton className="mt-4 h-28 w-full" />
        </div>
      </div>
    </div>
  )
}
