import { describe, expect, it } from "vitest"

import {
  contrast,
  coverLayout,
  dominantAccent,
  ensureReadableOnBlack,
  PLATFORM_ACCENT,
  textOn,
} from "./color"

function image(pixels: [number, number, number][]): Uint8ClampedArray {
  return new Uint8ClampedArray(pixels.flatMap(([r, g, b]) => [r, g, b, 255]))
}

function repeat<T>(value: T, times: number): T[] {
  return Array.from({ length: times }, () => value)
}

describe("dominantAccent", () => {
  it("pega o vermelho de um flyer vermelho e preto", () => {
    const flyer = image([
      ...repeat<[number, number, number]>([232, 38, 44], 40),
      ...repeat<[number, number, number]>([10, 10, 10], 60),
    ])
    const accent = dominantAccent(flyer)!
    expect(accent).toBe("#e8262c")
  })

  it("ignora foto em preto e branco", () => {
    const flyer = image([
      ...repeat<[number, number, number]>([20, 20, 20], 50),
      ...repeat<[number, number, number]>([200, 200, 200], 50),
    ])
    expect(dominantAccent(flyer)).toBeNull()
  })

  it("ignora cor que aparece em quase nada da imagem", () => {
    const flyer = image([
      [0, 120, 255],
      ...repeat<[number, number, number]>([230, 230, 230], 99),
    ])
    expect(dominantAccent(flyer)).toBeNull()
  })
})

describe("ensureReadableOnBlack", () => {
  it("mantém cor que já se lê no preto", () => {
    expect(ensureReadableOnBlack(PLATFORM_ACCENT)).toBe(PLATFORM_ACCENT)
  })

  it("clareia azul-marinho até passar de 4,5:1", () => {
    const fixed = ensureReadableOnBlack("#1a237e")
    expect(contrast(fixed, "#000000")).toBeGreaterThanOrEqual(4.5)
  })
})

describe("textOn", () => {
  it("usa preto no verde e no amarelo, branco no roxo escuro", () => {
    expect(textOn(PLATFORM_ACCENT)).toBe("#000000")
    expect(textOn("#ffc20e")).toBe("#000000")
    expect(textOn("#4b1585")).toBe("#ffffff")
  })
})

describe("coverLayout", () => {
  it("escolhe a capa pela proporção", () => {
    expect(coverLayout(918, 1600)).toBe("story")
    expect(coverLayout(1179, 1446)).toBe("feed")
    expect(coverLayout(1500, 1000)).toBe("wide")
    expect(coverLayout(1080, 1080)).toBe("wide")
  })
})
