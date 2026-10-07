import { Card, CardContent, CardHeader } from "@/components/ui/card"
import { Skeleton } from "@/components/ui/skeleton"

/** Placeholder das telas de acesso enquanto a parte dinâmica carrega. */
export function AuthCardSkeleton() {
  return (
    <Card aria-busy="true">
      <CardHeader className="gap-2">
        <Skeleton className="h-7 w-32" />
        <Skeleton className="h-4 w-56" />
      </CardHeader>
      <CardContent className="grid gap-4">
        <Skeleton className="h-11 w-full" />
        <Skeleton className="h-11 w-full" />
        <Skeleton className="h-11 w-full" />
      </CardContent>
    </Card>
  )
}
