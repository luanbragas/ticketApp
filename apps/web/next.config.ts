import type { NextConfig } from "next"

const isDev = process.env.NODE_ENV !== "production"
const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"
/** Imagens do bucket (R2 em produção, S3Mock em dev). */
const mediaUrl = process.env.NEXT_PUBLIC_MEDIA_URL ?? "http://localhost:9090"

/**
 * Política completa em modo relatório até ser conferida no navegador (SECURITY.md §Entrada e saída).
 * Scripts inline do Next precisam de 'unsafe-inline' sem nonce; nonce obrigaria renderizar tudo por pedido.
 */
const fullPolicy = [
  "default-src 'self'",
  `script-src 'self' 'unsafe-inline'${isDev ? " 'unsafe-eval'" : ""}`,
  "style-src 'self' 'unsafe-inline'",
  `img-src 'self' data: blob: ${mediaUrl} https:`,
  "font-src 'self'",
  `connect-src 'self' ${apiUrl}${isDev ? " ws:" : ""}`,
  "media-src 'self' blob:",
  "worker-src 'self'",
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "object-src 'none'",
].join("; ")

/** O que já vale de verdade: nada disso quebra a página. */
const enforcedPolicy = [
  "frame-ancestors 'none'",
  "base-uri 'self'",
  "form-action 'self'",
  "object-src 'none'",
].join("; ")

const securityHeaders = [
  { key: "Content-Security-Policy", value: enforcedPolicy },
  { key: "Content-Security-Policy-Report-Only", value: fullPolicy },
  { key: "X-Content-Type-Options", value: "nosniff" },
  { key: "Referrer-Policy", value: "strict-origin-when-cross-origin" },
  // Câmera só para a portaria (mesma origem); nada de microfone ou localização.
  {
    key: "Permissions-Policy",
    value: "camera=(self), microphone=(), geolocation=()",
  },
  ...(isDev
    ? []
    : [
        {
          key: "Strict-Transport-Security",
          value: "max-age=63072000; includeSubDomains; preload",
        },
      ]),
]

const nextConfig: NextConfig = {
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
  async headers() {
    return [{ source: "/:path*", headers: securityHeaders }]
  },
}

export default nextConfig
