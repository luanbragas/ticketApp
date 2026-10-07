import Link from "next/link"

import { Logo } from "@/components/brand/logo"
import { Button } from "@/components/ui/button"

/** Página inicial provisória; a landing da plataforma vem no M9. */
export default function Home() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-6 px-4 py-16 text-center">
      <Logo className="text-4xl" />
      <p className="max-w-sm text-muted-foreground">
        Ingressos para festas universitárias, atléticas e produtores
        independentes.
      </p>
      <div className="flex w-full max-w-xs flex-col gap-3 sm:max-w-none sm:flex-row sm:justify-center">
        <Button asChild size="lg" className="h-11">
          <Link href="/cadastro">Criar conta</Link>
        </Button>
        <Button asChild size="lg" variant="outline" className="h-11">
          <Link href="/entrar">Entrar</Link>
        </Button>
      </div>
    </main>
  )
}
