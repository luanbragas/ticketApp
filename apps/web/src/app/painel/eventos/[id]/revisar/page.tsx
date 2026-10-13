import type { Metadata } from "next"

import { editorOrganization, NoPermission } from "../../editor-gate"
import { ReviewStep } from "../../review-step"
import { WizardHeader } from "../../wizard-header"
import { WizardPage } from "../../wizard-page"

export const metadata: Metadata = { title: "Revisar evento" }

export default function Page({
  params,
}: PageProps<"/painel/eventos/[id]/revisar">) {
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
      <WizardHeader step="revisar" eventId={id} title="Evento" />
      {canEdit ? (
        <ReviewStep organizationId={organization.id} eventId={id} />
      ) : (
        <NoPermission />
      )}
    </>
  )
}
