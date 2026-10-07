"use client"

import { zodResolver } from "@hookform/resolvers/zod"
import { MailCheckIcon } from "lucide-react"
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
import { login, requestMagicLink } from "@/lib/api/auth"
import { applyApiError, rememberNext, safeNext } from "@/lib/forms"
import {
  loginSchema,
  magicLinkSchema,
  type LoginInput,
  type MagicLinkInput,
} from "@/lib/schemas/auth"
import { useSessionChanged } from "@/lib/session"

export function LoginForm({ next }: { next?: string }) {
  const [mode, setMode] = useState<"password" | "magic-link">("password")
  const [sentTo, setSentTo] = useState<string | null>(null)
  const signupHref = next
    ? `/cadastro?next=${encodeURIComponent(next)}`
    : "/cadastro"

  if (sentTo) {
    return (
      <Card>
        <CardHeader>
          <MailCheckIcon className="mb-2 size-8 text-primary" aria-hidden />
          <CardTitle className="text-2xl">Confira seu e-mail</CardTitle>
          <CardDescription>
            Se houver uma conta para{" "}
            <strong className="text-foreground">{sentTo}</strong>, enviamos um
            link de acesso. Ele vale por 15 minutos.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <Button
            variant="outline"
            className="h-11 w-full"
            onClick={() => setSentTo(null)}
          >
            Usar outro e-mail
          </Button>
        </CardContent>
      </Card>
    )
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle className="text-2xl">Entrar</CardTitle>
        <CardDescription>
          {mode === "password"
            ? "Acesse o painel da sua organização."
            : "Enviamos um link de acesso para o seu e-mail, sem senha."}
        </CardDescription>
      </CardHeader>
      <CardContent className="grid gap-6">
        {mode === "password" ? (
          <PasswordForm next={next} />
        ) : (
          <MagicLinkForm next={next} onSent={setSentTo} />
        )}

        <Button
          variant="ghost"
          className="h-11"
          onClick={() =>
            setMode(mode === "password" ? "magic-link" : "password")
          }
        >
          {mode === "password"
            ? "Receber link de acesso por e-mail"
            : "Entrar com senha"}
        </Button>

        <Separator />

        <p className="text-center text-sm text-muted-foreground">
          Ainda não tem conta?{" "}
          <Link
            href={signupHref}
            className="font-medium text-foreground underline underline-offset-4"
          >
            Criar conta
          </Link>
        </p>
      </CardContent>
    </Card>
  )
}

function PasswordForm({ next }: { next?: string }) {
  const router = useRouter()
  const onSessionChanged = useSessionChanged()
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<LoginInput>({ resolver: zodResolver(loginSchema) })

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null)
    try {
      await login(values)
      onSessionChanged()
      router.replace(safeNext(next))
      router.refresh()
    } catch (error) {
      setFormError(applyApiError(error, setError, ["email", "password"]))
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      <FormError message={formError} />
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
        autoComplete="current-password"
        registration={register("password")}
        error={errors.password}
      />
      <Button type="submit" size="lg" className="h-11" disabled={isSubmitting}>
        {isSubmitting ? "Entrando…" : "Entrar"}
      </Button>
    </form>
  )
}

function MagicLinkForm({
  next,
  onSent,
}: {
  next?: string
  onSent: (email: string) => void
}) {
  const [formError, setFormError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<MagicLinkInput>({ resolver: zodResolver(magicLinkSchema) })

  const onSubmit = handleSubmit(async ({ email }) => {
    setFormError(null)
    try {
      await requestMagicLink(email)
      rememberNext(next)
      onSent(email)
    } catch (error) {
      setFormError(applyApiError(error, setError, ["email"]))
    }
  })

  return (
    <form onSubmit={onSubmit} noValidate className="grid gap-4">
      <FormError message={formError} />
      <TextField
        label="E-mail"
        type="email"
        autoComplete="email"
        inputMode="email"
        registration={register("email")}
        error={errors.email}
      />
      <Button type="submit" size="lg" className="h-11" disabled={isSubmitting}>
        {isSubmitting ? "Enviando…" : "Enviar link"}
      </Button>
    </form>
  )
}
