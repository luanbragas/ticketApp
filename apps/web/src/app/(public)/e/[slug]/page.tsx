import { MapPinIcon } from "lucide-react"
import type { Metadata } from "next"
import { notFound } from "next/navigation"
import { Suspense } from "react"

import { Logo } from "@/components/brand/logo"
import { Skeleton } from "@/components/ui/skeleton"
import type { PublicEvent } from "@/lib/api/types"
import { coverLayout, PLATFORM_ACCENT, textOn } from "@/lib/color"
import { hourRange, longDay, mapsUrl, shortDay } from "@/lib/event-format"
import { getPublicEvent } from "@/lib/public-events"

import { Countdown, ShareButton } from "./event-client"
import { PromoterCapture } from "./promoter-capture"
import { BuyBar, ShopProvider, TicketList } from "./tickets-client"

export async function generateMetadata({
  params,
}: PageProps<"/e/[slug]">): Promise<Metadata> {
  const { slug } = await params
  const event = await getPublicEvent(slug)
  if (!event) return { title: "Evento não encontrado" }
  const when = `${longDay(event.startsAt)} · ${hourRange(event.startsAt, event.endsAt)}`
  const description = `${when} · ${event.venueName}. ${event.organizer.name} apresenta.`
  return {
    title: event.name,
    description,
    openGraph: {
      title: event.name,
      description,
      type: "website",
      locale: "pt_BR",
      images: event.flyer
        ? [
            {
              url: event.flyer.url,
              width: event.flyer.width,
              height: event.flyer.height,
              alt: `Flyer de ${event.name}`,
            },
          ]
        : undefined,
    },
    twitter: { card: "summary_large_image" },
  }
}

export default function EventPage({ params }: PageProps<"/e/[slug]">) {
  return (
    <Suspense fallback={<EventSkeleton />}>
      <EventContent params={params} />
    </Suspense>
  )
}

async function EventContent({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params
  const event = await getPublicEvent(slug)
  if (!event) notFound()
  return <EventView event={event} />
}

function EventView({ event }: { event: PublicEvent }) {
  const accent = event.accentColor ?? PLATFORM_ACCENT
  const ended = event.status === "ENDED"
  const layout = event.flyer
    ? coverLayout(event.flyer.width, event.flyer.height)
    : null
  const where = [event.address, event.city].filter(Boolean).join(" · ")

  return (
    <div
      className="relative flex min-h-dvh flex-1 flex-col overflow-x-clip bg-background pb-28 md:pb-12"
      style={
        {
          "--accent": accent,
          "--on-accent": textOn(accent),
        } as React.CSSProperties
      }
    >
      {/* Brilho da cor da festa no topo. */}
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-[-20%] top-[-120px] h-[520px] opacity-35"
        style={{
          background: `radial-gradient(ellipse at 50% 40%, ${accent}, transparent 65%)`,
        }}
      />

      <header className="relative z-10 mx-auto flex h-15 w-full max-w-6xl items-center justify-between px-5">
        <Logo href="/" className="font-display text-2xl" />
        <ShareButton title={event.name} />
      </header>

      <div className="relative mx-auto grid w-full max-w-6xl md:grid-cols-[minmax(0,5fr)_minmax(0,6fr)] md:gap-12 md:px-5 md:pt-6">
        {event.flyer && (
          <Cover
            url={event.flyer.url}
            name={event.name}
            layout={layout!}
            ratio={`${event.flyer.width} / ${event.flyer.height}`}
          />
        )}

        <ShopProvider slug={event.slug}>
          <PromoterCapture slug={event.slug} />
          <main
            className={
              layout === "story"
                ? "relative -mt-16 px-5 md:mt-0 md:px-0"
                : "relative mt-5 px-5 md:mt-0 md:px-0"
            }
          >
            {ended ? (
              <span className="inline-block bg-secondary px-3 py-1 text-xs font-extrabold tracking-wide uppercase">
                Essa festa já aconteceu
              </span>
            ) : (
              <Countdown startsAt={event.startsAt} />
            )}
            <h1
              className={`mt-3 font-display leading-[0.86] font-black uppercase ${
                event.name.length > 14
                  ? "text-5xl md:text-7xl"
                  : "text-6xl md:text-8xl"
              }`}
            >
              {event.name}
            </h1>
            <p className="mt-2 text-sm text-muted-foreground">
              {event.organizer.name} apresenta
              {event.minAge >= 18 ? " · 18+" : ""}
              {event.hasOpenBar ? " · open bar" : ""}
            </p>

            <dl className="-mx-5 mt-5 grid grid-cols-3 border-y md:mx-0">
              <Fact label="Data" value={shortDay(event.startsAt)} first />
              <Fact
                label="Horário"
                value={hourRange(event.startsAt, event.endsAt)}
              />
              <Fact label="Local" value={event.venueName} />
            </dl>

            {!ended && <TicketList />}

            {event.lineup.length > 0 && (
              <section aria-labelledby="noite" className="mt-6">
                <h2
                  id="noite"
                  className="font-display text-2xl font-black uppercase"
                >
                  A noite
                </h2>
                <ol className="mt-1">
                  {event.lineup.map((act) => (
                    <li
                      key={act.name}
                      className="grid grid-cols-[4rem_minmax(0,1fr)] items-baseline gap-3 border-b border-secondary py-2.5"
                    >
                      <span className="font-display text-2xl font-extrabold text-(--accent)">
                        {act.startsAt ? hourRange(act.startsAt, null) : "—"}
                      </span>
                      <span className="font-bold">{act.name}</span>
                    </li>
                  ))}
                </ol>
              </section>
            )}

            {event.description && (
              <section aria-labelledby="sobre" className="mt-6">
                <h2
                  id="sobre"
                  className="font-display text-2xl font-black uppercase"
                >
                  Sobre
                </h2>
                <p className="mt-2 leading-relaxed whitespace-pre-line text-muted-foreground">
                  {event.description}
                </p>
              </section>
            )}

            <a
              href={mapsUrl([event.venueName, event.address, event.city])}
              target="_blank"
              rel="noreferrer"
              className="mt-6 flex min-h-16 items-center gap-3 border-y py-3 hover:bg-muted"
            >
              <MapPinIcon
                className="size-5 shrink-0 text-(--accent)"
                aria-hidden
              />
              <span className="min-w-0 flex-1">
                <span className="block font-bold">{event.venueName}</span>
                {where && (
                  <span className="block text-sm text-muted-foreground">
                    {where}
                  </span>
                )}
              </span>
              <span className="text-sm font-bold">Como chegar</span>
            </a>

            <div className="fixed inset-x-0 bottom-0 z-20 border-t bg-background px-3 pt-2.5 pb-[calc(env(safe-area-inset-bottom)+1.25rem)] md:static md:mt-8 md:border-0 md:p-0">
              <BuyBar ended={ended} />
            </div>
          </main>
        </ShopProvider>
      </div>
    </div>
  )
}

function Cover({
  url,
  name,
  layout,
  ratio,
}: {
  url: string
  name: string
  layout: "story" | "feed" | "wide"
  ratio: string
}) {
  const alt = `Flyer de ${name}`
  if (layout === "story") {
    return (
      <div className="relative -mt-15 h-[470px] overflow-hidden md:mt-0 md:h-auto md:max-h-[80dvh] md:self-start">
        {/* eslint-disable-next-line @next/next/no-img-element -- imagem do bucket do produtor */}
        <img
          src={url}
          alt={alt}
          className="size-full object-cover object-[50%_12%] md:h-auto md:object-contain"
          style={{ aspectRatio: ratio }}
        />
        <div
          aria-hidden
          className="absolute inset-0 bg-[linear-gradient(180deg,rgba(0,0,0,0.55)_0,transparent_16%,transparent_60%,#000_100%)] md:hidden"
        />
      </div>
    )
  }
  if (layout === "feed") {
    return (
      <div className="mx-5 overflow-hidden md:mx-0 md:self-start">
        {/* eslint-disable-next-line @next/next/no-img-element -- imagem do bucket do produtor */}
        <img
          src={url}
          alt={alt}
          className="max-h-[360px] w-full object-cover md:max-h-none"
          style={{ aspectRatio: ratio }}
        />
      </div>
    )
  }
  return (
    <div className="relative flex h-72 items-center justify-center overflow-hidden md:self-start">
      {/* eslint-disable-next-line @next/next/no-img-element -- fundo desfocado decorativo */}
      <img
        src={url}
        alt=""
        aria-hidden
        className="absolute inset-[-30px] size-[calc(100%+60px)] object-cover blur-2xl brightness-50"
      />
      {/* eslint-disable-next-line @next/next/no-img-element -- imagem do bucket do produtor */}
      <img
        src={url}
        alt={alt}
        className="relative max-h-64 w-[calc(100%-2.5rem)] object-cover"
        style={{ aspectRatio: ratio }}
      />
    </div>
  )
}

function Fact({
  label,
  value,
  first = false,
}: {
  label: string
  value: string
  first?: boolean
}) {
  return (
    <div
      className={`min-w-0 border-r py-3 pr-2 last:border-r-0 ${first ? "pl-5 md:pl-0" : "pl-3.5"}`}
    >
      <dt className="text-xs font-bold text-muted-foreground">{label}</dt>
      <dd className="mt-0.5 font-display text-2xl leading-none font-extrabold break-words">
        {value}
      </dd>
    </div>
  )
}

function EventSkeleton() {
  return (
    <div aria-busy="true" className="mx-auto w-full max-w-xl">
      <Skeleton className="h-[470px] w-full rounded-none" />
      <div className="grid gap-3 px-5 pt-5">
        <Skeleton className="h-6 w-24" />
        <Skeleton className="h-14 w-3/4" />
        <Skeleton className="h-4 w-1/2" />
        <Skeleton className="mt-4 h-14 w-full" />
      </div>
    </div>
  )
}
