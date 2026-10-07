import type { Metadata } from "next"
import { Suspense } from "react"

import { AuthCardSkeleton } from "@/components/form/auth-card-skeleton"

import { SignupForm } from "./signup-form"

export const metadata: Metadata = { title: "Criar conta" }

export default function SignupPage({ searchParams }: PageProps<"/cadastro">) {
  return (
    <Suspense fallback={<AuthCardSkeleton />}>
      <SignupWithNext searchParams={searchParams} />
    </Suspense>
  )
}

async function SignupWithNext({
  searchParams,
}: Pick<PageProps<"/cadastro">, "searchParams">) {
  const { next } = await searchParams
  return <SignupForm next={typeof next === "string" ? next : undefined} />
}
