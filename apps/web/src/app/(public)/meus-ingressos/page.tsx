import type { Metadata } from "next"

import { Logo } from "@/components/brand/logo"

import { MyTicketsView } from "./my-tickets"

export const metadata: Metadata = {
  title: "Meus ingressos",
  robots: { index: false },
}

export default function MyTicketsPage() {
  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-xl flex-col px-5 pb-16">
      <header className="flex h-15 items-center">
        <Logo href="/" className="font-display text-2xl" />
      </header>
      <h1 className="mt-2 font-display text-5xl leading-[0.9] font-black uppercase">
        Meus ingressos
      </h1>
      <MyTicketsView />
    </div>
  )
}
