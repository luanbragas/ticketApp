import { describe, expect, it } from "vitest"

import { loginSchema, signupSchema } from "./auth"
import {
  createOrganizationSchema,
  invitableRoles,
  slugify,
} from "./organization"

describe("slugify (mesma regra do Slug.java)", () => {
  it("remove acentos e símbolos", () => {
    expect(slugify("Atlética de Medicina — UFMG!")).toBe(
      "atletica-de-medicina-ufmg",
    )
    expect(slugify("  Calourada   2026  ")).toBe("calourada-2026")
    expect(slugify("Ação & Coração")).toBe("acao-coracao")
  })

  it("corta em 60 caracteres sem hífen no fim", () => {
    const slug = slugify(`${"a".repeat(59)} bbb`)
    expect(slug.length).toBeLessThanOrEqual(60)
    expect(slug.endsWith("-")).toBe(false)
  })
})

describe("invitableRoles (mesma regra do InvitationPolicy.java)", () => {
  it("dono convida todos menos dono; admin não convida admin", () => {
    expect(invitableRoles("OWNER")).toEqual([
      "ADMIN",
      "MANAGER",
      "PROMOTER",
      "CHECKIN_OPERATOR",
    ])
    expect(invitableRoles("ADMIN")).toEqual([
      "MANAGER",
      "PROMOTER",
      "CHECKIN_OPERATOR",
    ])
  })

  it("demais papéis não convidam", () => {
    expect(invitableRoles("MANAGER")).toEqual([])
    expect(invitableRoles("PROMOTER")).toEqual([])
    expect(invitableRoles("CHECKIN_OPERATOR")).toEqual([])
  })
})

describe("schemas", () => {
  it("cadastro exige senha de 8 caracteres e e-mail válido", () => {
    const result = signupSchema.safeParse({
      name: " ",
      email: "x",
      password: "curta",
    })
    expect(result.success).toBe(false)
    const messages = result.error?.issues.map((issue) => issue.message)
    expect(messages).toContain("Informe seu nome.")
    expect(messages).toContain("E-mail inválido.")
    expect(messages).toContain("A senha deve ter pelo menos 8 caracteres.")
  })

  it("login apara espaços do e-mail", () => {
    const result = loginSchema.parse({
      email: "  ana@festa.com ",
      password: "x",
    })
    expect(result.email).toBe("ana@festa.com")
  })

  it("organização rejeita endereço fora do padrão", () => {
    const result = createOrganizationSchema.safeParse({
      name: "Atlética",
      slug: "Atlética Med",
    })
    expect(result.success).toBe(false)
    expect(result.error?.issues[0].message).toBe(
      "Use só letras minúsculas, números e hífens.",
    )
  })
})
