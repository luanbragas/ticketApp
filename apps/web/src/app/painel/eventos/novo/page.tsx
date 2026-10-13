import type { Metadata } from "next"

import { editorOrganization, NoPermission } from "../editor-gate"
import { InfoStep } from "../info-step"
import { WizardHeader } from "../wizard-header"
import { WizardPage } from "../wizard-page"

export const metadata: Metadata = { title: "Novo evento" }

export default function NewEventPage() {
  return (
    <WizardPage>
      <NewEvent />
    </WizardPage>
  )
}

async function NewEvent() {
  const { organization, canEdit } = await editorOrganization()
  return (
    <>
      <WizardHeader step="informacoes" title="Novo evento" />
      {canEdit ? (
        <InfoStep organizationId={organization.id} />
      ) : (
        <NoPermission />
      )}
    </>
  )
}
