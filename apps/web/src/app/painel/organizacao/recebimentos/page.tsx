import type { Metadata } from "next"
import { redirect } from "next/navigation"
import { Suspense } from "react"

import { Skeleton } from "@/components/ui/skeleton"
import { getCurrentOrganization } from "@/lib/auth"

import { NoPermission } from "../../eventos/editor-gate"
import { PaymentAccount } from "./payment-account"

export const metadata: Metadata = { title: "Recebimentos" }

export default function Page() {
  return (
    <Suspense fallback={<Skeleton className="h-64 w-full" />}>
      <Account />
    </Suspense>
  )
}

async function Account() {
  const organization = await getCurrentOrganization()
  if (!organization) redirect("/painel")
  return (
    <div className="grid max-w-xl gap-6">
      <div>
        <p className="text-sm text-muted-foreground">{organization.name}</p>
        <h1 className="mt-1 font-display text-5xl leading-[0.9] font-black uppercase">
          Recebimentos
        </h1>
      </div>
      {organization.role === "OWNER" || organization.role === "ADMIN" ? (
        <PaymentAccount organizationId={organization.id} />
      ) : (
        <NoPermission message="A conta de recebimento é conectada pelo dono e pelos administradores." />
      )}
    </div>
  )
}
