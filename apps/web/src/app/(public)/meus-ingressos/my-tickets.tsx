"use client"

import { useQuery } from "@tanstack/react-query"
import Link from "next/link"

import { FormError } from "@/components/form/form-error"
import { Skeleton } from "@/components/ui/skeleton"
import { api, ApiError } from "@/lib/api/client"
import type { MyTickets, PublicTicket } from "@/lib/api/types"
import { hourRange, shortDay } from "@/lib/event-format"
import { cn } from "@/lib/utils"

const SIGN_IN = "/entrar?modo=link&next=%2Fmeus-ingressos"

/** Ingressos do e-mail da sessão (link mágico, ADR-004 e ADR-008): próximos primeiro, depois o histórico. */
export function MyTicketsView() {
  const tickets = useQuery({
    queryKey: ["my-tickets"],
    queryFn: () => api<MyTickets>("/api/v1/me/tickets"),
    retry: false,
  })

  if (tickets.isPending) {
    return (
      <div aria-busy="true" className="mt-8 grid gap-3">
        <Skeleton className="h-24 w-full" />
        <Skeleton className="h-24 w-full" />
      </div>
    )
  }
  if (tickets.isError) {
    const error = tickets.error
    if (
      error instanceof ApiError &&
      (error.status === 401 || error.code === "email-not-verified")
    ) {
      return <SignIn />
    }
    return <FormError message={error.message} />
  }
  const { upcoming, past } = tickets.data
  if (upcoming.length === 0 && past.length === 0) {
    return (
      <p className="mt-6 text-muted-foreground">
        Nenhum ingresso neste e-mail ainda. Ingressos comprados com outro e-mail
        aparecem quando você entra com ele.
      </p>
    )
  }
  return (
    <div className="mt-8 grid gap-10">
      {upcoming.length > 0 && <Group title="Próximos" tickets={upcoming} />}
      {past.length > 0 && <Group title="Já foram" tickets={past} muted />}
    </div>
  )
}

function SignIn() {
  return (
    <div className="mt-6 grid gap-4">
      <p className="max-w-sm text-muted-foreground">
        Entre com o e-mail que você usou na compra. Mandamos um link de acesso,
        sem senha.
      </p>
      <Link
        href={SIGN_IN}
        className="flex h-14 max-w-sm items-center justify-between bg-primary px-5 font-display text-2xl font-black text-primary-foreground uppercase"
      >
        Receber link
        <span aria-hidden>→</span>
      </Link>
    </div>
  )
}

function Group({
  title,
  tickets,
  muted = false,
}: {
  title: string
  tickets: PublicTicket[]
  muted?: boolean
}) {
  return (
    <section aria-label={title} className="grid gap-2">
      <h2 className="border-b border-foreground pb-2 font-display text-3xl leading-none font-black uppercase">
        {title}
      </h2>
      <ul>
        {tickets.map((ticket) => (
          <li key={ticket.token} className="border-b">
            <Link
              href={`/t/${ticket.token}`}
              className={cn(
                "flex min-h-20 items-center gap-4 py-3 hover:bg-muted",
                muted && "text-muted-foreground",
              )}
            >
              <span
                aria-hidden
                className="h-14 w-1.5 shrink-0"
                style={{
                  background: muted
                    ? "var(--secondary)"
                    : (ticket.event.accentColor ?? "var(--primary)"),
                }}
              />
              <span className="min-w-0 flex-1">
                <span className="block truncate font-display text-2xl leading-tight font-black uppercase">
                  {ticket.event.name}
                </span>
                <span className="block truncate text-sm text-muted-foreground">
                  {shortDay(ticket.event.startsAt)} ·{" "}
                  {hourRange(ticket.event.startsAt, null)} · {ticket.holderName}{" "}
                  · {ticket.typeName}
                </span>
              </span>
              {ticket.status !== "VALID" && (
                <span className="shrink-0 text-xs font-extrabold uppercase">
                  {ticket.status === "CHECKED_IN" ? "Usado" : "Inválido"}
                </span>
              )}
            </Link>
          </li>
        ))}
      </ul>
    </section>
  )
}
