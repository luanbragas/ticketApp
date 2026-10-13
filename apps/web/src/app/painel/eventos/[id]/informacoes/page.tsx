import type { Metadata } from "next"

import { editorOrganization, NoPermission } from "../../editor-gate"
import { InfoStep } from "../../info-step"
import { WizardHeader } from "../../wizard-header"
import { WizardPage } from "../../wizard-page"

export const metadata: Metadata = { title: "Informações do evento" }

export default function Page({
  params,
}: PageProps<"/painel/eventos/[id]/informacoes">) {
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
      <WizardHeader step="informacoes" eventId={id} title="Evento" />
      {canEdit ? (
        <InfoStep organizationId={organization.id} eventId={id} />
      ) : (
        <NoPermission />
      )}
    </>
  )
}
