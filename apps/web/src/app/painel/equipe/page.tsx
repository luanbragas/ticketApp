import { LockIcon } from "lucide-react"
import type { Metadata } from "next"
import { redirect } from "next/navigation"

import { getCurrentOrganization } from "@/lib/auth"

import { TeamView } from "./team-view"

export const metadata: Metadata = { title: "Equipe" }

export default async function TeamPage() {
  const organization = await getCurrentOrganization()
  if (!organization) redirect("/painel")

  const canSeeTeam = ["OWNER", "ADMIN", "MANAGER"].includes(organization.role)

  return (
    <div className="grid gap-6">
      <div>
        <p className="text-sm text-muted-foreground">{organization.name}</p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight md:text-3xl">
          Equipe
        </h1>
      </div>
      {canSeeTeam ? (
        <TeamView organizationId={organization.id} myRole={organization.role} />
      ) : (
        <div className="flex items-start gap-3 rounded-xl border bg-background p-5 text-sm text-muted-foreground">
          <LockIcon className="mt-0.5 size-4 shrink-0" aria-hidden />A lista da
          equipe é visível só para dono, administradores e gerentes.
        </div>
      )}
    </div>
  )
}
