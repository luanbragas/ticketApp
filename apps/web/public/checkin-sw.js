/*
 * Service worker da portaria (escopo /checkin/): guarda a página e os arquivos do Next para a tela abrir
 * de novo em modo avião (ADR-010). Página: rede primeiro, cópia guardada se cair. Arquivos estáticos
 * (com hash no nome): cache primeiro. Não guarda nada da API.
 */
const CACHE = "festa-checkin-v1"

self.addEventListener("install", () => self.skipWaiting())

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) =>
        Promise.all(
          keys.filter((k) => k !== CACHE).map((k) => caches.delete(k)),
        ),
      )
      .then(() => self.clients.claim()),
  )
})

self.addEventListener("fetch", (event) => {
  const request = event.request
  if (request.method !== "GET") return
  const url = new URL(request.url)
  if (url.origin !== self.location.origin) return

  if (request.mode === "navigate") {
    event.respondWith(
      fetch(request)
        .then((response) => {
          const copy = response.clone()
          caches.open(CACHE).then((cache) => cache.put(request, copy))
          return response
        })
        .catch(() =>
          caches.match(request).then((hit) => hit ?? Response.error()),
        ),
    )
    return
  }

  if (
    url.pathname.startsWith("/_next/static/") ||
    url.pathname.startsWith("/fonts/")
  ) {
    event.respondWith(
      caches.match(request).then(
        (hit) =>
          hit ??
          fetch(request).then((response) => {
            const copy = response.clone()
            caches.open(CACHE).then((cache) => cache.put(request, copy))
            return response
          }),
      ),
    )
  }
})
