import { z } from "zod"

import type { Role } from "@/lib/api/types"

/** "Atlética de Medicina — UFMG" → "atletica-de-medicina-ufmg" (mesma regra do backend). */
export function slugify(name: string): string {
  return name
    .normalize("NFD")
    .replace(/\p{M}/gu, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 60)
    .replace(/-+$/, "")
}

export const createOrganizationSchema = z.object({
  name: z
    .string()
    .trim()
    .min(1, { error: "Informe o nome da organização." })
    .max(80, { error: "Nome muito longo." }),
  slug: z
    .string()
    .trim()
    .min(3, { error: "O endereço deve ter entre 3 e 60 caracteres." })
    .max(60, { error: "O endereço deve ter entre 3 e 60 caracteres." })
    .regex(/^[a-z0-9]+(-[a-z0-9]+)*$/, {
      error: "Use só letras minúsculas, números e hífens.",
    }),
})

export type CreateOrganizationInput = z.infer<typeof createOrganizationSchema>

/** Papéis que cada um pode dar num convite (InvitationPolicy no backend). */
export function invitableRoles(myRole: Role): Role[] {
  switch (myRole) {
    case "OWNER":
      return ["ADMIN", "MANAGER", "PROMOTER", "CHECKIN_OPERATOR"]
    case "ADMIN":
      return ["MANAGER", "PROMOTER", "CHECKIN_OPERATOR"]
    default:
      return []
  }
}

export const inviteSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, { error: "Informe o e-mail." })
    .pipe(z.email({ error: "E-mail inválido." })),
  role: z.enum(["ADMIN", "MANAGER", "PROMOTER", "CHECKIN_OPERATOR"], {
    error: "Escolha um papel.",
  }),
})

export type InviteInput = z.infer<typeof inviteSchema>
