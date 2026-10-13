// Carga na página do evento (PLAN.md M9): o pico de quem abre o link no grupo do WhatsApp.
//
//   k6 run -e WEB=http://localhost:3000 -e API=http://localhost:8080 -e SLUG=calourada-teste infra/load/event-page.js
//
// Cada usuário virtual abre a página (HTML do Next) e os dados públicos do evento na API, espera e repete.
import http from "k6/http"
import { check, sleep } from "k6"

const WEB = __ENV.WEB || "http://localhost:3000"
const API = __ENV.API || "http://localhost:8080"
const SLUG = __ENV.SLUG || "calourada-teste"

export const options = {
  scenarios: {
    link_no_grupo: {
      executor: "ramping-vus",
      startVUs: 0,
      stages: [
        { duration: "20s", target: Number(__ENV.VUS || 100) },
        { duration: "40s", target: Number(__ENV.VUS || 100) },
        { duration: "10s", target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{kind:page}": ["p(95)<800"],
    "http_req_duration{kind:api}": ["p(95)<300"],
  },
}

export default function () {
  const page = http.get(`${WEB}/e/${SLUG}`, { tags: { kind: "page" } })
  check(page, { "página 200": (r) => r.status === 200 })
  const event = http.get(`${API}/api/v1/public/events/${SLUG}`, {
    tags: { kind: "api" },
  })
  check(event, { "evento 200": (r) => r.status === 200 })
  sleep(1 + Math.random() * 2)
}
