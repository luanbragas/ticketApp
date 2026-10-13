import type { Metadata, Viewport } from "next"
import Link from "next/link"
import { Suspense } from "react"

import { Skeleton } from "@/components/ui/skeleton"
import { getCurrentOrganization, requireUser } from "@/lib/auth"

import { GateApp } from "./gate-app"

export const metadata: Metadata = {
  title: "Portaria",
  robots: { index: false },
}

export const viewport: Viewport = { themeColor: "#000000" }

const OPERATORS = ["OWNER", "ADMIN", "MANAGER", "CHECKIN_OPERATOR"]

export default function CheckinPage({
  params,
}: PageProps<"/checkin/[eventId]">) {
  return (
    <Suspense fallback={<Skeleton className="m-4 h-[80dvh]" />}>
      <Gate params={params} />
    </Suspense>
  )
}

async function Gate({ params }: { params: Promise<{ eventId: string }> }) {
  const { eventId } = await params
  await requireUser(`/checkin/${eventId}`)
  const organization = await getCurrentOrganization()
  if (!organization || !OPERATORS.includes(organization.role)) {
    return (
      <div className="grid min-h-dvh place-content-center gap-3 px-6 text-center">
        <p className="font-display text-4xl font-black uppercase">Sem acesso</p>
        <p className="text-muted-foreground">
          A portaria é para a equipe do evento com papel de check-in, gerente,
          admin ou dono.
        </p>
        <Link href="/painel" className="font-bold underline underline-offset-4">
          Voltar ao painel
        </Link>
      </div>
    )
  }
  return <GateApp organizationId={organization.id} eventId={eventId} />
}
