import { MapPinIcon } from "lucide-react"
import type { Metadata } from "next"
import { notFound } from "next/navigation"
import QRCode from "qrcode"
import { Suspense } from "react"

import { Logo } from "@/components/brand/logo"
import { Skeleton } from "@/components/ui/skeleton"
import type { PublicTicket, TicketStatus } from "@/lib/api/types"
import { googleCalendarUrl, icsContent } from "@/lib/calendar"
import { PLATFORM_ACCENT, textOn } from "@/lib/color"
import { hourRange, longDay, mapsUrl, shortDay } from "@/lib/event-format"
import { getPublicTicket } from "@/lib/public-tickets"

/** O token é o ingresso: nada de indexar nem vazar o endereço para outros sites. */
export const metadata: Metadata = {
  title: "Seu ingresso",
  robots: { index: false, follow: false },
  referrer: "no-referrer",
}

const STATUS: Record<TicketStatus, { label: string; live: boolean }> = {
  VALID: { label: "Válido", live: true },
  CHECKED_IN: { label: "Já entrou", live: false },
  TRANSFERRED: { label: "Transferido", live: false },
  CANCELLED: { label: "Cancelado", live: false },
}

export default function TicketPage({ params }: PageProps<"/t/[token]">) {
  return (
    <Suspense fallback={<TicketSkeleton />}>
      <TicketContent params={params} />
    </Suspense>
  )
}

async function TicketContent({
  params,
}: {
  params: Promise<{ token: string }>
}) {
  const { token } = await params
  const ticket = await getPublicTicket(token)
  if (!ticket) notFound()
  // SVG gerado aqui a partir do token já validado (só [A-Za-z0-9_-]); nada do usuário entra no SVG.
  const qr = await QRCode.toString(ticket.token, {
    type: "svg",
    errorCorrectionLevel: "M",
    margin: 0,
    color: { dark: "#000000", light: "#ffffff" },
  })
  return <TicketView ticket={ticket} qr={qr} />
}

function TicketView({ ticket, qr }: { ticket: PublicTicket; qr: string }) {
  const { event } = ticket
  const accent = event.accentColor ?? PLATFORM_ACCENT
  const status = STATUS[ticket.status]
  const where = [event.venueName, event.address, event.city]
    .filter(Boolean)
    .join(", ")
  const calendar = {
    title: event.name,
    startsAt: event.startsAt,
    endsAt: event.endsAt,
    location: where,
    details: `Ingresso de ${ticket.holderName} (${ticket.typeName}). Leve documento com foto.`,
  }

  return (
    <div
      className="relative mx-auto flex min-h-dvh w-full max-w-md flex-col overflow-x-clip px-5 pb-12"
      style={
        {
          "--accent": accent,
          "--on-accent": textOn(accent),
        } as React.CSSProperties
      }
    >
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-[-30%] top-[-140px] h-[420px] opacity-30"
        style={{
          background: `radial-gradient(ellipse at 50% 40%, ${accent}, transparent 65%)`,
        }}
      />
      <header className="relative flex h-15 items-center justify-between">
        <Logo href="/" className="font-display text-2xl" />
        <span
          className={
            status.live
              ? "bg-(--accent) px-2.5 py-1 text-xs font-extrabold tracking-wide text-(--on-accent) uppercase"
              : "bg-secondary px-2.5 py-1 text-xs font-extrabold tracking-wide uppercase"
          }
        >
          {status.label}
        </span>
      </header>

      <h1 className="relative mt-2 font-display text-5xl leading-[0.88] font-black uppercase">
        {event.name}
      </h1>
      <p className="relative mt-2 text-sm text-muted-foreground">
        {shortDay(event.startsAt)} · {hourRange(event.startsAt, event.endsAt)}
      </p>

      <figure className="relative mt-6 grid gap-3">
        <div
          className={`bg-white p-5 ${status.live ? "" : "opacity-25 grayscale"}`}
          role="img"
          aria-label={`QR Code do ingresso de ${ticket.holderName}`}
          dangerouslySetInnerHTML={{ __html: qr }}
        />
        <figcaption className="text-center text-sm text-muted-foreground">
          {status.live
            ? "Mostre este QR Code e um documento com foto na entrada."
            : "Este QR Code não vale mais para entrar."}
        </figcaption>
      </figure>

      <dl className="relative mt-6 grid grid-cols-2 border-y">
        <div className="col-span-2 border-b py-3">
          <dt className="text-xs font-bold text-muted-foreground">Titular</dt>
          <dd className="mt-0.5 font-display text-3xl leading-none font-extrabold uppercase">
            {ticket.holderName}
          </dd>
          <dd className="mt-1 text-sm text-muted-foreground">
            CPF {ticket.holderCpf}
          </dd>
        </div>
        <div className="border-r py-3 pr-3">
          <dt className="text-xs font-bold text-muted-foreground">Ingresso</dt>
          <dd className="mt-0.5 font-bold">{ticket.typeName}</dd>
          <dd className="text-sm text-muted-foreground">{ticket.batchName}</dd>
        </div>
        <div className="py-3 pl-3">
          <dt className="text-xs font-bold text-muted-foreground">Quando</dt>
          <dd className="mt-0.5 font-bold first-letter:uppercase">
            {longDay(event.startsAt)}
          </dd>
          <dd className="text-sm text-muted-foreground">
            {hourRange(event.startsAt, event.endsAt)}
          </dd>
        </div>
      </dl>

      {ticket.halfPrice && (
        <p className="relative mt-4 border-l-4 border-(--accent) pl-3 text-sm">
          Meia-entrada: leve o documento que dá direito ao benefício. Sem ele, a
          diferença é cobrada na porta.
        </p>
      )}

      {event.venueName && (
        <a
          href={mapsUrl([event.venueName, event.address, event.city])}
          target="_blank"
          rel="noreferrer"
          className="relative mt-4 flex min-h-16 items-center gap-3 border-b py-3 hover:bg-muted"
        >
          <MapPinIcon className="size-5 shrink-0 text-(--accent)" aria-hidden />
          <span className="min-w-0 flex-1">
            <span className="block font-bold">{event.venueName}</span>
            {(event.address || event.city) && (
              <span className="block text-sm text-muted-foreground">
                {[event.address, event.city].filter(Boolean).join(" · ")}
              </span>
            )}
          </span>
          <span className="text-sm font-bold">Como chegar</span>
        </a>
      )}

      <div className="relative mt-6 grid grid-cols-2 gap-2">
        <a
          href={googleCalendarUrl(calendar)}
          target="_blank"
          rel="noreferrer"
          className="flex h-12 items-center justify-center border border-input text-sm font-extrabold"
        >
          Google Agenda
        </a>
        <a
          href={`data:text/calendar;charset=utf-8,${encodeURIComponent(icsContent(calendar, event.slug))}`}
          download={`${event.slug}.ics`}
          className="flex h-12 items-center justify-center border border-input text-sm font-extrabold"
        >
          Apple / Outlook
        </a>
      </div>

      <p className="relative mt-8 text-center text-xs text-muted-foreground">
        Não compartilhe este link: quem tiver o QR entra no lugar do titular.
      </p>
    </div>
  )
}

function TicketSkeleton() {
  return (
    <div
      aria-busy="true"
      className="mx-auto grid w-full max-w-md gap-4 px-5 pt-20"
    >
      <Skeleton className="h-14 w-3/4" />
      <Skeleton className="aspect-square w-full" />
      <Skeleton className="h-24 w-full" />
    </div>
  )
}
