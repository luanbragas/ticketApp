import type { Metadata } from "next"

import { InvitationView } from "./invitation-view"

export const metadata: Metadata = { title: "Convite" }

export default function InvitationPage() {
  return <InvitationView />
}
