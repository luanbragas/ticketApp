import Link from "next/link"

import { Logo } from "@/components/brand/logo"

/** Evento inexistente, em rascunho ou cancelado (a API não diferencia, de propósito). */
export default function EventNotFound() {
  return (
    <div className="flex min-h-dvh flex-1 flex-col bg-background">
      <header className="flex h-15 items-center px-5">
        <Logo href="/" className="font-display text-2xl" />
      </header>
      <main className="mx-auto w-full max-w-xl flex-1 px-5 pt-16">
        <span
          aria-hidden
          className="block font-display text-[120px] leading-[0.8] font-black text-[#6b6b6b]"
        >
          404
        </span>
        <h1 className="mt-4 font-display text-6xl leading-[0.9] font-black uppercase">
          Essa festa
          <br />
          não tá aqui
        </h1>
        <p className="mt-3 max-w-sm text-muted-foreground">
          O link pode estar errado ou o evento foi cancelado. Se você comprou, o
          reembolso vai para o mesmo meio de pagamento.
        </p>
        <Link
          href="/"
          className="mt-8 flex h-14 items-center justify-between bg-primary px-5 text-primary-foreground"
        >
          <span className="font-display text-2xl font-black uppercase">
            Ver outras festas
          </span>
          <span aria-hidden>→</span>
        </Link>
      </main>
    </div>
  )
}
