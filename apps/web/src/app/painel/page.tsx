import {
  ArrowRightIcon,
  CalendarPlusIcon,
  PartyPopperIcon,
  UsersIcon,
} from "lucide-react"
import type { Metadata } from "next"
import Link from "next/link"

import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { ROLE_LABELS } from "@/lib/api/types"
import { getCurrentOrganization, requireUser } from "@/lib/auth"

export const metadata: Metadata = { title: "Painel" }

export default async function PanelHome() {
  const [user, organization] = await Promise.all([
    requireUser(),
    getCurrentOrganization(),
  ])

  if (!organization) {
    return (
      <div className="mx-auto flex max-w-md flex-col items-center py-10 text-center">
        <div className="mb-5 flex size-14 items-center justify-center rounded-full bg-primary/10">
          <PartyPopperIcon className="size-7 text-primary" aria-hidden />
        </div>
        <h1 className="text-2xl font-semibold tracking-tight">
          Crie sua organização
        </h1>
        <p className="mt-2 text-muted-foreground">
          Atlética, coletivo ou produtora: é por ela que você cria eventos,
          vende ingressos e chama sua equipe.
        </p>
        <Button asChild size="lg" className="mt-6 h-11 w-full sm:w-auto">
          <Link href="/painel/organizacoes/nova">Criar organização</Link>
        </Button>
      </div>
    )
  }

  const canManageTeam = ["OWNER", "ADMIN", "MANAGER"].includes(
    organization.role,
  )

  return (
    <div className="grid gap-8">
      <div>
        <p className="text-sm text-muted-foreground">
          {organization.name} · {ROLE_LABELS[organization.role]}
        </p>
        <h1 className="mt-1 text-2xl font-semibold tracking-tight md:text-3xl">
          {user.name ? `Olá, ${user.name.split(" ")[0]}!` : "Olá!"}
        </h1>
      </div>

      <section aria-labelledby="proximos-passos" className="grid gap-3">
        <h2
          id="proximos-passos"
          className="text-sm font-medium text-muted-foreground"
        >
          Próximos passos
        </h2>
        <div className="grid gap-3 md:grid-cols-2">
          {canManageTeam && (
            <Link
              href="/painel/equipe"
              className="group rounded-xl focus-visible:outline-none"
            >
              <Card className="h-full transition-colors group-hover:border-primary/50 group-focus-visible:ring-2 group-focus-visible:ring-ring">
                <CardHeader>
                  <UsersIcon className="mb-1 size-5 text-primary" aria-hidden />
                  <CardTitle className="flex items-center justify-between gap-2">
                    Monte sua equipe
                    <ArrowRightIcon
                      className="size-4 text-muted-foreground transition-transform group-hover:translate-x-0.5"
                      aria-hidden
                    />
                  </CardTitle>
                  <CardDescription>
                    Convide administradores, promoters e quem vai fazer o
                    check-in na portaria.
                  </CardDescription>
                </CardHeader>
              </Card>
            </Link>
          )}
          <Card className="h-full border-dashed">
            <CardHeader>
              <CalendarPlusIcon
                className="mb-1 size-5 text-muted-foreground"
                aria-hidden
              />
              <CardTitle className="flex items-center justify-between gap-2">
                Crie seu primeiro evento
                <Badge variant="secondary">Em breve</Badge>
              </CardTitle>
              <CardDescription>
                Página do evento, lotes e venda por PIX e cartão chegam nas
                próximas etapas.
              </CardDescription>
            </CardHeader>
          </Card>
        </div>
      </section>
    </div>
  )
}
