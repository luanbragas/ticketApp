import { Suspense } from "react"

import { Skeleton } from "@/components/ui/skeleton"

/**
 * Moldura das páginas do wizard. A sessão e os params são lidos dentro do Suspense
 * (Cache Components): o esqueleto aparece na hora e o passo chega em streaming.
 */
export function WizardPage({ children }: { children: React.ReactNode }) {
  return (
    <div className="mx-auto grid max-w-xl gap-8">
      <Suspense fallback={<WizardSkeleton />}>{children}</Suspense>
    </div>
  )
}

function WizardSkeleton() {
  return (
    <div aria-busy="true" className="grid gap-6">
      <Skeleton className="h-4 w-48" />
      <Skeleton className="h-12 w-full" />
      <Skeleton className="h-12 w-64" />
      <Skeleton className="h-16 w-full" />
      <Skeleton className="h-16 w-full" />
    </div>
  )
}
