import { LockIcon } from "lucide-react"
import { redirect } from "next/navigation"

import type { Organization } from "@/lib/api/types"
import { getCurrentOrganization } from "@/lib/auth"

const EDITORS = ["OWNER", "ADMIN", "MANAGER"]

/** Organização atual de quem pode criar e editar eventos; sem organização volta ao painel. */
export async function editorOrganization(): Promise<{
  organization: Organization
  canEdit: boolean
}> {
  const organization = await getCurrentOrganization()
  if (!organization) redirect("/painel")
  return { organization, canEdit: EDITORS.includes(organization.role) }
}

export function NoPermission({
  message = "Criar e editar eventos é para dono, administradores e gerentes.",
}: {
  message?: string
}) {
  return (
    <div className="flex items-start gap-3 border p-5 text-sm text-muted-foreground">
      <LockIcon className="mt-0.5 size-4 shrink-0" aria-hidden />
      {message}
    </div>
  )
}
