import type { Metadata, Viewport } from "next"
import { Big_Shoulders, Manrope } from "next/font/google"
import "./globals.css"
import { Providers } from "./providers"

// Tema G: Manrope no texto, Big Shoulders Display nos títulos e números grandes.
const manrope = Manrope({
  variable: "--font-sans",
  subsets: ["latin"],
})

const display = Big_Shoulders({
  variable: "--font-display",
  subsets: ["latin"],
  weight: ["800", "900"],
  // O Next não tem métricas de fallback para esta fonte; sem isso ele avisa a cada página.
  adjustFontFallback: false,
})

export const metadata: Metadata = {
  title: {
    default: "FESTA",
    template: "%s · FESTA",
  },
  description: "Ingressos para festas universitárias, atléticas e produtores.",
}

export const viewport: Viewport = {
  width: "device-width",
  initialScale: 1,
  themeColor: "#000000",
}

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    <html
      lang="pt-BR"
      className={`${manrope.variable} ${display.variable} dark h-full antialiased`}
    >
      <body className="flex min-h-full flex-col">
        <Providers>{children}</Providers>
      </body>
    </html>
  )
}
