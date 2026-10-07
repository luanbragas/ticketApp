import Link from "next/link"

import { cn } from "@/lib/utils"

/** Marca provisória: nome com o ponto na cor da marca. */
export function Logo({
  className,
  href = "/",
}: {
  className?: string
  href?: string
}) {
  return (
    <Link
      href={href}
      className={cn(
        "inline-flex items-baseline text-xl font-black tracking-tight",
        className,
      )}
    >
      FESTA<span className="text-primary">.</span>
    </Link>
  )
}
