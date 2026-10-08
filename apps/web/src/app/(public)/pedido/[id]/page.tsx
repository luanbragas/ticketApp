import type { Metadata } from "next"
import { Suspense } from "react"

import { Logo } from "@/components/brand/logo"
import { Skeleton } from "@/components/ui/skeleton"

import { OrderView } from "./order-view"

export const metadata: Metadata = {
  title: "Seu pedido",
  robots: { index: false },
}

export default function OrderPage({ params }: PageProps<"/pedido/[id]">) {
  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-xl flex-col px-5 pb-16">
      <header className="flex h-15 items-center">
        <Logo href="/" className="font-display text-2xl" />
      </header>
      <Suspense fallback={<Skeleton className="mt-6 h-64 w-full" />}>
        <Order params={params} />
      </Suspense>
    </div>
  )
}

async function Order({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params
  return <OrderView orderId={id} />
}
