import { Logo } from "@/components/brand/logo"

/** Telas de acesso: uma coluna, cartão central no desktop e tela cheia no celular. */
export default function AuthLayout({
  children,
}: {
  children: React.ReactNode
}) {
  return (
    <div className="flex flex-1 flex-col bg-muted/40">
      <header className="px-4 py-5 sm:px-6">
        <Logo />
      </header>
      <main className="flex flex-1 items-start justify-center px-4 pb-16 sm:items-center">
        <div className="w-full max-w-sm">{children}</div>
      </main>
    </div>
  )
}
