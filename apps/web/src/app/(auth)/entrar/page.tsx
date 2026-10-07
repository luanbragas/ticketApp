import type { Metadata } from "next"
import { Suspense } from "react"

import { AuthCardSkeleton } from "@/components/form/auth-card-skeleton"

import { LoginForm } from "./login-form"

export const metadata: Metadata = { title: "Entrar" }

export default function LoginPage({ searchParams }: PageProps<"/entrar">) {
  return (
    <Suspense fallback={<AuthCardSkeleton />}>
      <LoginWithNext searchParams={searchParams} />
    </Suspense>
  )
}

async function LoginWithNext({
  searchParams,
}: Pick<PageProps<"/entrar">, "searchParams">) {
  const { next } = await searchParams
  return <LoginForm next={typeof next === "string" ? next : undefined} />
}
