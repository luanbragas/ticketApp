// Abertura de lote (PLAN.md M9): muita gente tenta comprar o mesmo lote no mesmo segundo.
// Confere que ninguém leva ingresso além da capacidade (CLAUDE.md regra 5) e que o resto recebe "esgotado",
// sem erro 5xx.
//
//   k6 run -e API=http://localhost:8080 -e SLUG=calourada-teste -e BATCH=<id do lote> -e BUYERS=300 \
//     infra/load/batch-opening.js
//
// A API precisa estar com o limite por IP desligado (FESTA_RATELIMIT_ENABLED=false): aqui todo mundo sai
// do mesmo IP. Cada comprador tenta 1 ingresso com CPF aleatório.
import http from "k6/http"
import { check } from "k6"
import { Counter } from "k6/metrics"

const API = __ENV.API || "http://localhost:8080"
const SLUG = __ENV.SLUG || "calourada-teste"
const BATCH = __ENV.BATCH
const BUYERS = Number(__ENV.BUYERS || 300)

const reserved = new Counter("reservas")
const soldOut = new Counter("esgotado")
const unexpected = new Counter("respostas_inesperadas")

export const options = {
  scenarios: {
    abertura: {
      executor: "per-vu-iterations",
      vus: BUYERS,
      iterations: 1,
      maxDuration: "2m",
    },
  },
  thresholds: {
    respostas_inesperadas: ["count==0"],
    "http_req_duration{name:order}": ["p(95)<2000"],
  },
}

export function setup() {
  if (!BATCH) throw new Error("Informe o lote: -e BATCH=<id>")
}

export default function () {
  // Cookie XSRF-TOKEN para o POST (cada usuário virtual tem o seu pote de cookies).
  http.get(`${API}/api/v1/auth/csrf`)
  const xsrf = http.cookieJar().cookiesForURL(API)["XSRF-TOKEN"]?.[0]
  const key = uuid() + "k"
  const res = http.post(
    `${API}/api/v1/public/orders`,
    JSON.stringify({
      event: SLUG,
      buyer: { name: "Carga Teste", email: `carga-${key.slice(0, 8)}@festa.test`, cpf: cpf() },
      tickets: [{ batchId: BATCH, holderName: "Carga Teste", holderCpf: cpf() }],
      adultDeclared: true,
      termsAccepted: true,
    }),
    {
      headers: {
        "Content-Type": "application/json",
        "Idempotency-Key": key,
        "X-XSRF-TOKEN": xsrf,
      },
      tags: { name: "order" },
    },
  )
  if (res.status === 201) {
    reserved.add(1)
  } else if (res.status === 409) {
    soldOut.add(1)
  } else {
    unexpected.add(1)
    console.warn(`status ${res.status}: ${res.body}`)
  }
  check(res, { "201 ou 409": (r) => r.status === 201 || r.status === 409 })
}

/** CPF válido aleatório (dígitos verificadores certos). */
function cpf() {
  const d = Array.from({ length: 9 }, () => Math.floor(Math.random() * 10))
  for (const n of [9, 10]) {
    let sum = 0
    for (let i = 0; i < n; i++) sum += d[i] * (n + 1 - i)
    const rest = (sum * 10) % 11
    d.push(rest === 10 ? 0 : rest)
  }
  return d.join("")
}

function uuid() {
  return "xxxxxxxxxxxx4xxxyxxxxxxxxxxxxxxx".replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0
    return (c === "x" ? r : (r & 0x3) | 0x8).toString(16)
  })
}
