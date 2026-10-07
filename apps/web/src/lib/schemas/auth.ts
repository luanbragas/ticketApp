import { z } from "zod"

/** Mesmas regras da API (AuthController); mensagens em português. */

const email = z
  .string()
  .trim()
  .min(1, { error: "Informe seu e-mail." })
  .max(320, { error: "E-mail muito longo." })
  .pipe(z.email({ error: "E-mail inválido." }))

export const loginSchema = z.object({
  email,
  password: z
    .string()
    .min(1, { error: "Informe sua senha." })
    .max(128, { error: "Senha muito longa." }),
})

export const signupSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, { error: "Informe seu nome." })
    .max(120, { error: "Nome muito longo." }),
  email,
  password: z
    .string()
    .min(8, { error: "A senha deve ter pelo menos 8 caracteres." })
    .max(128, { error: "A senha deve ter no máximo 128 caracteres." }),
})

export const magicLinkSchema = z.object({ email })

export type LoginInput = z.infer<typeof loginSchema>
export type SignupInput = z.infer<typeof signupSchema>
export type MagicLinkInput = z.infer<typeof magicLinkSchema>
