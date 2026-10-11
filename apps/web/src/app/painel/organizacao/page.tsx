import type { Metadata } from "next"
import { redirect } from "next/navigation"
import { Suspense } from "react"

import { Skeleton } from "@/components/ui/skeleton"
import { getCurrentOrganization } from "@/lib/auth"

import { NoPermission } from "../eventos/editor-gate"
import { OrganizationProfile } from "./organization-profile"

export const metadata: Metadata = { title: "Dados da organização" }

export default function Page() {
  return (
    <Suspense fallback={<Skeleton className="h-64 w-full" />}>
      <Profile />
    </Suspense>
  )
}

async function Profile() {
  const organization = await getCurrentOrganization()
  if (!organization) redirect("/painel")
  return (
    <div className="grid max-w-xl gap-6">
      <div>
        <p className="text-sm text-muted-foreground">
          Aparece na página das festas
        </p>
        <h1 className="mt-1 font-display text-5xl leading-[0.9] font-black uppercase">
          Organização
        </h1>
      </div>
      {organization.role === "OWNER" || organization.role === "ADMIN" ? (
        <OrganizationProfile organization={organization} />
      ) : (
        <NoPermission message="Os dados da organização são editados pelo dono e pelos administradores." />
      )}
    </div>
  )
}
