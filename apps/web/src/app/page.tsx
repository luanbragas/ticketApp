import type { Metadata } from "next"
import Link from "next/link"

import { Logo } from "@/components/brand/logo"

export const metadata: Metadata = {
  title: "FESTA · Ingressos para festa universitária",
  description:
    "Venda ingressos da sua festa por PIX e cartão, com lotes que viram sozinhos, links de promoter e portaria que funciona sem internet.",
}

const FEATURES = [
  {
    title: "Compra em 1 minuto",
    text: "PIX ou cartão, sem o comprador criar conta. O ingresso chega no e-mail com QR Code.",
  },
  {
    title: "Lote vira sozinho",
    text: "Esgotou ou chegou a data, abre o próximo. Meia-entrada com a cota da lei já calculada.",
  },
  {
    title: "Promoter com link",
    text: "Cada promoter tem o seu link. Você vê quem vendeu quanto, e ele vê só as vendas dele.",
  },
  {
    title: "Portaria sem internet",
    text: "Leitura do QR no celular, até em modo avião. Sincroniza depois, sem ninguém entrar duas vezes.",
  },
]

const STEPS = [
  {
    n: "1",
    title: "Cria a festa",
    text: "Data, local e o flyer. A página pega a cor do flyer.",
  },
  {
    n: "2",
    title: "Monta os lotes",
    text: "Pista, camarote, meia. Preço, quantidade e quando vira.",
  },
  {
    n: "3",
    title: "Divulga e vende",
    text: "Link na bio, no grupo e com os promoters. Acompanha tudo no painel.",
  },
]

/** Landing da plataforma (PLAN.md M9): para quem produz; quem comprou vai direto aos ingressos. */
export default function Home() {
  return (
    <div className="relative flex min-h-dvh flex-col overflow-x-clip bg-background">
      <div
        aria-hidden
        className="pointer-events-none absolute inset-x-[-30%] top-[-160px] h-[520px] opacity-25"
        style={{
          background:
            "radial-gradient(ellipse at 50% 40%, var(--primary), transparent 65%)",
        }}
      />
      <header className="relative mx-auto flex h-16 w-full max-w-6xl items-center justify-between px-5">
        <Logo className="font-display text-3xl" />
        <nav className="flex items-center gap-4 text-sm font-bold">
          <Link href="/meus-ingressos" className="hover:text-primary">
            Meus ingressos
          </Link>
          <Link href="/entrar" className="hover:text-primary">
            Entrar
          </Link>
        </nav>
      </header>

      <main className="relative mx-auto w-full max-w-6xl flex-1 px-5">
        <section className="pt-12 pb-16 md:pt-20">
          <p className="text-sm font-extrabold tracking-wide text-primary uppercase">
            Para atléticas, DAs e produtores
          </p>
          <h1 className="mt-3 max-w-4xl font-display text-6xl leading-[0.85] font-black uppercase md:text-8xl">
            Sua festa vendendo hoje à noite
          </h1>
          <p className="mt-5 max-w-xl text-lg text-muted-foreground">
            Página bonita da festa, PIX e cartão, lotes, meia-entrada, promoters
            e portaria no celular. Tudo num lugar só.
          </p>
          <div className="mt-8 grid max-w-md gap-3 sm:flex">
            <Link
              href="/cadastro?next=%2Fpainel%2Feventos%2Fnovo"
              className="flex h-14 items-center justify-between gap-6 bg-primary px-5 font-display text-2xl font-black text-primary-foreground uppercase"
            >
              Criar minha festa
              <span aria-hidden>→</span>
            </Link>
            <Link
              href="/entrar"
              className="flex h-14 items-center justify-center border border-input px-5 font-bold"
            >
              Já tenho conta
            </Link>
          </div>
          <p className="mt-3 text-sm text-muted-foreground">
            Criar a festa é grátis. A taxa de serviço é paga por quem compra.
          </p>
        </section>

        <section
          aria-labelledby="como"
          className="border-t border-foreground py-12"
        >
          <h2 id="como" className="font-display text-4xl font-black uppercase">
            Como funciona
          </h2>
          <ol className="mt-6 grid gap-px bg-border md:grid-cols-3">
            {STEPS.map((step) => (
              <li
                key={step.n}
                className="bg-background py-5 md:px-5 md:first:pl-0"
              >
                <span className="font-display text-6xl leading-none font-black text-primary">
                  {step.n}
                </span>
                <p className="mt-2 font-display text-2xl font-black uppercase">
                  {step.title}
                </p>
                <p className="mt-1 text-muted-foreground">{step.text}</p>
              </li>
            ))}
          </ol>
        </section>

        <section
          aria-labelledby="recursos"
          className="border-t border-foreground py-12"
        >
          <h2
            id="recursos"
            className="font-display text-4xl font-black uppercase"
          >
            Feito para festa de faculdade
          </h2>
          <ul className="mt-6 grid gap-px bg-border sm:grid-cols-2">
            {FEATURES.map((feature) => (
              <li key={feature.title} className="bg-background py-5 sm:pr-6">
                <p className="font-display text-2xl font-black uppercase">
                  {feature.title}
                </p>
                <p className="mt-1 text-muted-foreground">{feature.text}</p>
              </li>
            ))}
          </ul>
        </section>

        <section className="border-t border-foreground py-14 text-center">
          <p className="mx-auto max-w-2xl font-display text-5xl leading-[0.9] font-black uppercase">
            A próxima calourada já pode ter link
          </p>
          <Link
            href="/cadastro?next=%2Fpainel%2Feventos%2Fnovo"
            className="mx-auto mt-6 flex h-14 max-w-sm items-center justify-between bg-primary px-5 font-display text-2xl font-black text-primary-foreground uppercase"
          >
            Criar minha festa
            <span aria-hidden>→</span>
          </Link>
        </section>
      </main>

      <footer className="relative mx-auto flex w-full max-w-6xl flex-wrap items-center justify-between gap-3 border-t px-5 py-6 text-sm text-muted-foreground">
        <Logo className="font-display text-xl" />
        <p>
          Comprou um ingresso? Ele está em{" "}
          <Link href="/meus-ingressos" className="underline underline-offset-4">
            Meus ingressos
          </Link>
          .
        </p>
        <p className="flex gap-4">
          <Link href="/termos" className="underline underline-offset-4">
            Termos
          </Link>
          <Link href="/privacidade" className="underline underline-offset-4">
            Privacidade
          </Link>
        </p>
      </footer>
    </div>
  )
}
