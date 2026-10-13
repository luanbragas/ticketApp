/**
 * Cor de destaque da página do evento a partir do flyer (ADR-005).
 * Funções puras sobre pixels RGBA, para rodar no navegador e em teste.
 */

export const PLATFORM_ACCENT = "#d7ff1f"

/** Contraste mínimo da cor sobre o fundo preto (WCAG AA para texto). */
const MIN_CONTRAST_ON_BLACK = 4.5

type Rgb = [number, number, number]

export function hexToRgb(hex: string): Rgb {
  const value = hex.replace("#", "")
  return [0, 2, 4].map((i) => parseInt(value.slice(i, i + 2), 16)) as Rgb
}

export function rgbToHex([r, g, b]: Rgb): string {
  return (
    "#" +
    [r, g, b]
      .map((c) =>
        Math.round(Math.min(255, Math.max(0, c)))
          .toString(16)
          .padStart(2, "0"),
      )
      .join("")
  )
}

/** Luminância relativa (WCAG 2.x). */
export function luminance(hex: string): number {
  const [r, g, b] = hexToRgb(hex).map((c) => {
    const s = c / 255
    return s <= 0.03928 ? s / 12.92 : ((s + 0.055) / 1.055) ** 2.4
  })
  return 0.2126 * r + 0.7152 * g + 0.0722 * b
}

export function contrast(a: string, b: string): number {
  const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x)
  return (hi + 0.05) / (lo + 0.05)
}

/** Clareia a cor até ter contraste suficiente sobre o preto. */
export function ensureReadableOnBlack(hex: string): string {
  let rgb = hexToRgb(hex)
  for (
    let i = 0;
    i < 20 && contrast(rgbToHex(rgb), "#000000") < MIN_CONTRAST_ON_BLACK;
    i++
  ) {
    rgb = rgb.map((c) => c + (255 - c) * 0.12) as Rgb
  }
  return rgbToHex(rgb)
}

/** Texto em cima da cor: preto ou branco, o que tiver mais contraste. */
export function textOn(hex: string): "#000000" | "#ffffff" {
  return contrast(hex, "#ffffff") > contrast(hex, "#000000")
    ? "#ffffff"
    : "#000000"
}

/**
 * Cor mais marcante do flyer: agrupa pixels saturados e de luminosidade média por matiz e pega o
 * grupo com mais peso. Sem cor forte (foto P&B, tudo bege), devolve null e a página usa o verde
 * da plataforma.
 */
export function dominantAccent(pixels: Uint8ClampedArray): string | null {
  const bins = new Map<
    number,
    { weight: number; r: number; g: number; b: number }
  >()
  let total = 0
  for (let i = 0; i < pixels.length; i += 4) {
    const r = pixels[i] / 255
    const g = pixels[i + 1] / 255
    const b = pixels[i + 2] / 255
    if (pixels[i + 3] < 128) continue
    total++
    const max = Math.max(r, g, b)
    const min = Math.min(r, g, b)
    const light = (max + min) / 2
    const delta = max - min
    if (delta === 0) continue
    const saturation = delta / (1 - Math.abs(2 * light - 1))
    if (saturation < 0.45 || light < 0.2 || light > 0.8) continue
    let hue =
      max === r
        ? ((g - b) / delta) % 6
        : max === g
          ? (b - r) / delta + 2
          : (r - g) / delta + 4
    hue = (hue * 60 + 360) % 360
    const key = Math.round(hue / 24) % 15
    const bin = bins.get(key) ?? { weight: 0, r: 0, g: 0, b: 0 }
    bin.weight += saturation
    bin.r += pixels[i] * saturation
    bin.g += pixels[i + 1] * saturation
    bin.b += pixels[i + 2] * saturation
    bins.set(key, bin)
  }
  let best: { weight: number; r: number; g: number; b: number } | undefined
  for (const bin of bins.values())
    if (!best || bin.weight > best.weight) best = bin
  // Cor que aparece em menos de 3% da imagem não define a festa.
  if (!best || best.weight < total * 0.03) return null
  return rgbToHex([
    best.r / best.weight,
    best.g / best.weight,
    best.b / best.weight,
  ])
}

export type CoverLayout = "story" | "feed" | "wide"

/** Layout da capa pela proporção do flyer (ADR-005). */
export function coverLayout(width: number, height: number): CoverLayout {
  const ratio = height / width
  if (ratio >= 1.5) return "story"
  if (ratio >= 1.1) return "feed"
  return "wide"
}
