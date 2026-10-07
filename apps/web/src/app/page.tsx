import { Button } from "@/components/ui/button"

export default function Home() {
  return (
    <main className="flex flex-1 flex-col items-center justify-center gap-6 px-4 py-16 text-center">
      <h1 className="text-3xl font-semibold tracking-tight">FESTA</h1>
      <p className="max-w-sm text-muted-foreground">
        Ingressos para festas universitárias, atléticas e produtores
        independentes. Em construção.
      </p>
      <Button size="lg" disabled>
        Em breve
      </Button>
    </main>
  )
}
