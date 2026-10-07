import { Skeleton } from "@/components/ui/skeleton"

/** Esqueleto do conteúdo enquanto a página do painel busca dados (limite de Suspense por página). */
export default function PanelLoading() {
  return (
    <div aria-busy="true" className="grid gap-4">
      <Skeleton className="h-4 w-40" />
      <Skeleton className="h-8 w-56" />
      <div className="mt-4 grid gap-3 md:grid-cols-2">
        <Skeleton className="h-32 w-full rounded-xl" />
        <Skeleton className="h-32 w-full rounded-xl" />
      </div>
    </div>
  )
}
