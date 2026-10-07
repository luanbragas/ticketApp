import type { Metadata } from "next"

import { CreateOrganizationForm } from "./create-organization-form"

export const metadata: Metadata = { title: "Criar organização" }

export default function NewOrganizationPage() {
  return (
    <div className="mx-auto grid max-w-lg gap-6">
      <div>
        <h1 className="text-2xl font-semibold tracking-tight">
          Criar organização
        </h1>
        <p className="mt-1 text-muted-foreground">
          Você será o dono e poderá convidar sua equipe em seguida.
        </p>
      </div>
      <CreateOrganizationForm />
    </div>
  )
}
