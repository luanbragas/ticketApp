"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import Link from "next/link"
import { useRouter } from "next/navigation"
import { useState } from "react"
import { useForm } from "react-hook-form"

import { FormError } from "@/components/form/form-error"
import { TextField } from "@/components/form/text-field"
import { Button } from "@/components/ui/button"
import {
  Card,
  CardContent,
  CardDescription,
  CardHeader,
  CardTitle,
} from "@/components/ui/card"
import { Separator } from "@/components/ui/separator"
import { signup } from "@/lib/api/auth"
import { applyApiError, safeNext } from "@/lib/forms"
import { signupSchema, type SignupInput } from "@/lib/schemas/auth"
import { useSessionChanged } from "@/lib/session"

export function SignupForm({ next }: { next?: string }) {
  const router = useRouter()
  const onSessionChanged = useSessionChanged()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SignupInput>({ resolver: zodResolver(signupSchema) })
  const loginHref = next
    ? `/entrar?next=${encodeURIComponent(next)}`
    : "/entrar"

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await signup(values)
      onSessionChanged()
      router.replace(safeNext(next))
      router.refresh()
    } catch (error) {
      setFormError(
        applyApiError(error, setError, ["name", "email", "password"]),
      )
    }
  })

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-2xl">Criar conta</CardTitle>
        <CardDescription>
          Para criar eventos e vender ingressos da sua organização.
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-6">
        <form onSubmit={onSubmit} noValidate className="grid gap-4">
          <FormError message={formError} />
          <TextField
            label="Nome"
            autoComplete="name"
            registration={register("name")}
            error={errors.name}
          />
          <TextField
            label="E-mail"
            type="email"
            autoComplete="email"
            inputMode="email"
            registration={register("email")}
            error={errors.email}
          />
          <TextField
            label="Senha"
            type="password"
            autoComplete="new-password"
            hint="Pelo menos 8 caracteres."
            registration={register("password")}
            error={errors.password}
          />
          <Button
            type="submit"
            size="lg"
            className="h-11"
            disabled={isSubmitting}
          >
            {isSubmitting ? "Criando conta…" : "Criar conta"}
          </Button>
        </form>

        <Separator />

        <p className="text-center text-sm text-muted-foreground">
          Já tem conta?{" "}
          <Link
            href={loginHref}
            className="font-medium text-foreground underline underline-offset-4"
          >
            Entrar
          </Link>
        </p>
      </CardContent>
    </Card>
  )
}
