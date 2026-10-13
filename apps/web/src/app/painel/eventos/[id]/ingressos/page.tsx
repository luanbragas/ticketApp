import type { Metadata } from "next"

import { editorOrganization, NoPermission } from "../../editor-gate"
import { TicketsStep } from "../../tickets-step"
import { WizardHeader } from "../../wizard-header"
import { WizardPage } from "../../wizard-page"

export const metadata: Metadata = { title: "Ingressos do evento" }

export default function Page({
  params,
}: PageProps<"/painel/eventos/[id]/ingressos">) {
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
      <WizardHeader step="ingressos" eventId={id} title="Evento" />
      {canEdit ? (
        <TicketsStep organizationId={organization.id} eventId={id} />
      ) : (
        <NoPermission />
      )}
    </>
  )
}
