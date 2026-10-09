import { describe, expect, it } from "vitest"

import { promoterCookie, promoterFromCookies } from "./promoter-cookie"

describe("promoter-cookie", () => {
  it("grava o código por 7 dias no cookie do evento", () => {
    expect(promoterCookie("calourada", "Joao-Silva")).toBe(
      "festa_p_calourada=joao-silva; Max-Age=604800; Path=/; SameSite=Lax",
    )
  })

  it("ignora código que não é código", () => {
    expect(promoterCookie("calourada", "<script>")).toBeNull()
    expect(promoterCookie("calourada", "ab")).toBeNull()
  })

  it("lê o código do evento certo", () => {
    const cookies =
      "XSRF-TOKEN=abc; festa_p_outra=bia; festa_p_calourada=joao-silva"
    expect(promoterFromCookies(cookies, "calourada")).toBe("joao-silva")
    expect(promoterFromCookies(cookies, "sem-link")).toBeNull()
    expect(
      promoterFromCookies("festa_p_calourada=%3Cx%3E", "calourada"),
    ).toBeNull()
  })
})
