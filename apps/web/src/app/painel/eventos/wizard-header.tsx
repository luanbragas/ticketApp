import Link from "next/link"

import { cn } from "@/lib/utils"

/** Passos do wizard. As regras de venda (cota de meia, limite por CPF) ficam no passo Ingressos. */
export const WIZARD_STEPS = [
  { slug: "informacoes", label: "Informações" },
  { slug: "aparencia", label: "Aparência" },
  { slug: "ingressos", label: "Ingressos" },
  { slug: "revisar", label: "Revisar e publicar" },
] as const

export type WizardStep = (typeof WIZARD_STEPS)[number]["slug"]

/** Cabeçalho "passo N de 4" com barra de progresso; com evento salvo, os passos viram links. */
export function WizardHeader({
  step,
  eventId,
  title,
}: {
  step: WizardStep
  eventId?: string
  title: string
}) {
  const current = WIZARD_STEPS.findIndex((s) => s.slug === step)

  return (
    <div className="grid gap-4">
      <div className="flex items-center justify-between gap-4">
        <p className="text-sm font-bold text-muted-foreground">
          {title} · passo {current + 1} de {WIZARD_STEPS.length}
        </p>
        <Link
          href="/painel"
          className="text-sm font-bold text-muted-foreground underline-offset-4 hover:text-foreground hover:underline"
        >
          Sair{eventId ? " (já está salvo)" : ""}
        </Link>
      </div>
      <nav aria-label="Passos do evento">
        <ol className="grid grid-cols-4 gap-1">
          {WIZARD_STEPS.map((s, i) => {
            const done = i < current
            const active = i === current
            const content = (
              <>
                <span
                  aria-hidden
                  className={cn(
                    "block h-1",
                    done || active ? "bg-primary" : "bg-secondary",
                  )}
                />
                <span
                  className={cn(
                    "mt-2 block truncate text-xs font-bold",
                    active ? "text-foreground" : "text-muted-foreground",
                  )}
                >
                  {s.label}
                </span>
              </>
            )
            return (
              <li key={s.slug}>
                {eventId && !active ? (
                  <Link
                    href={`/painel/eventos/${eventId}/${s.slug}`}
                    className="block min-h-11 hover:text-foreground"
                  >
                    {content}
                  </Link>
                ) : (
                  <span
                    className="block min-h-11"
                    aria-current={active ? "step" : undefined}
                  >
                    {content}
                  </span>
                )}
              </li>
            )
          })}
        </ol>
      </nav>
      <h1 className="font-display text-5xl leading-[0.9] font-black uppercase">
        {WIZARD_STEPS[current].label}
      </h1>
    </div>
  )
}
