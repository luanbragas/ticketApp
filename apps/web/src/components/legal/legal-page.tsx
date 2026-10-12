import Link from "next/link"

import { Logo } from "@/components/brand/logo"

/**
 * Versão do texto mostrado. Precisa bater com `terms_versions` na API: texto novo = migration nova lá +
 * versão nova aqui (SECURITY.md §LGPD).
 */
export const LEGAL_VERSION = "rascunho-2026-10"

export type LegalSection = { title: string; paragraphs: string[] }

/** Página de texto jurídico: termos de uso ou política de privacidade. */
export function LegalPage({
  title,
  sections,
  other,
}: {
  title: string
  sections: LegalSection[]
  other: { href: string; label: string }
}) {
  return (
    <div className="mx-auto flex min-h-dvh w-full max-w-2xl flex-col px-5 pb-16">
      <header className="flex h-15 items-center">
        <Logo href="/" className="font-display text-2xl" />
      </header>
      <h1 className="mt-2 font-display text-5xl leading-[0.9] font-black uppercase">
        {title}
      </h1>
      <p className="mt-3 text-sm text-muted-foreground">
        Versão {LEGAL_VERSION}
      </p>
      <p role="note" className="mt-4 border-l-4 border-[#ffb020] pl-3 text-sm">
        Rascunho em revisão jurídica. Este texto resume as regras da plataforma
        e será substituído pela versão final antes do lançamento; a partir daí,
        cadastros e compras registram o aceite da versão final.
      </p>
      <div className="mt-8 grid gap-8">
        {sections.map((section) => (
          <section key={section.title} className="grid gap-2">
            <h2 className="border-b border-foreground pb-2 font-display text-2xl leading-none font-black uppercase">
              {section.title}
            </h2>
            {section.paragraphs.map((p) => (
              <p key={p} className="leading-relaxed">
                {p}
              </p>
            ))}
          </section>
        ))}
      </div>
      <p className="mt-10 text-sm text-muted-foreground">
        Veja também:{" "}
        <Link
          href={other.href}
          className="font-bold text-foreground underline underline-offset-4"
        >
          {other.label}
        </Link>
      </p>
    </div>
  )
}
