import type { Metadata } from "next"

import { editorOrganization, NoPermission } from "../../editor-gate"
import { AppearanceStep } from "../../appearance-step"
import { WizardHeader } from "../../wizard-header"
import { WizardPage } from "../../wizard-page"

export const metadata: Metadata = { title: "Aparência do evento" }

export default function Page({
  params,
}: PageProps<"/painel/eventos/[id]/aparencia">) {
  return (
    <WizardPage>
      <Step params={params} />
    </WizardPage>
  )
}

async function Step({ params }: { params: Promise<{ id: string }> }) {
  const [{ id }, { organization, canEdit }] = await Promise.all([
    params,
    editorOrganization(),
  ])
  return (
    <>
      <WizardHeader step="aparencia" eventId={id} title="Evento" />
      {canEdit ? (
        <AppearanceStep organizationId={organization.id} eventId={id} />
      ) : (
        <NoPermission />
      )}
    </>
  )
}
