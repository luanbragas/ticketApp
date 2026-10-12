import type { Metadata } from "next"
import { redirect } from "next/navigation"
import { Suspense } from "react"

import { Skeleton } from "@/components/ui/skeleton"
import { getCurrentOrganization } from "@/lib/auth"

import { NoPermission } from "../eventos/editor-gate"
import { FinanceView } from "./finance-view"

export const metadata: Metadata = { title: "Financeiro" }

export default function Page() {
  return (
    <Suspense fallback={<Skeleton className="h-64 w-full" />}>
      <Finance />
    </Suspense>
  )
}

async function Finance() {
  const organization = await getCurrentOrganization()
  if (!organization) redirect("/painel")
  return (
    <div className="grid gap-6">
      <div>
        <p className="text-sm text-muted-foreground">{organization.name}</p>
        <h1 className="mt-1 font-display text-5xl leading-[0.9] font-black uppercase">
          Financeiro
        </h1>
      </div>
      {["OWNER", "ADMIN", "MANAGER"].includes(organization.role) ? (
        <FinanceView organizationId={organization.id} />
      ) : (
        <NoPermission message="O financeiro é visível para dono, administradores e gerentes." />
      )}
    </div>
  )
}
