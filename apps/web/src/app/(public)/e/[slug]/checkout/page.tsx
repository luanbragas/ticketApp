import type { Metadata } from "next"
import Link from "next/link"
import { notFound } from "next/navigation"
import { Suspense } from "react"

import { Logo } from "@/components/brand/logo"
import { Skeleton } from "@/components/ui/skeleton"
import { PLATFORM_ACCENT, textOn } from "@/lib/color"
import { hourRange, shortDay } from "@/lib/event-format"
import { getPublicEvent } from "@/lib/public-events"

import { CheckoutForm } from "./checkout-form"

export const metadata: Metadata = {
  title: "Checkout",
  robots: { index: false },
}

export default function CheckoutPage({
  params,
}: PageProps<"/e/[slug]/checkout">) {
  return (
    <Suspense fallback={<CheckoutSkeleton />}>
      <Checkout params={params} />
    </Suspense>
  )
}

async function Checkout({ params }: { params: Promise<{ slug: string }> }) {
  const { slug } = await params
  const event = await getPublicEvent(slug)
  if (!event) notFound()
  const accent = event.accentColor ?? PLATFORM_ACCENT

  return (
    <div
      className="mx-auto flex min-h-dvh w-full max-w-xl flex-col bg-background px-5 pb-16"
      style={
        {
          "--accent": accent,
          "--on-accent": textOn(accent),
        } as React.CSSProperties
      }
    >
      <header className="flex h-15 items-center justify-between">
        <Logo href="/" className="font-display text-2xl" />
        <Link
          href={`/e/${slug}`}
          className="text-sm font-bold text-muted-foreground hover:text-foreground"
        >
          Voltar à festa
        </Link>
      </header>
      <p className="text-sm text-muted-foreground">
        {shortDay(event.startsAt)} · {hourRange(event.startsAt, event.endsAt)} ·{" "}
        {event.venueName}
      </p>
      <h1 className="mt-1 font-display text-5xl leading-[0.9] font-black uppercase">
        {event.name}
      </h1>
      <CheckoutForm slug={slug} adultsOnly={event.minAge >= 18} />
    </div>
  )
}

function CheckoutSkeleton() {
  return (
    <div
      aria-busy="true"
      className="mx-auto grid w-full max-w-xl gap-4 px-5 pt-20"
    >
      <Skeleton className="h-12 w-3/4" />
      <Skeleton className="h-32 w-full" />
      <Skeleton className="h-48 w-full" />
    </div>
  )
}
