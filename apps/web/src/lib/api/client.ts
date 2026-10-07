/**
 * Cliente HTTP do navegador para a API (ADR-001): sessão em cookie HttpOnly e CSRF
 * por cookie XSRF-TOKEN devolvido no header X-XSRF-TOKEN.
 */

export const API_URL =
  process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080"

export type FieldError = { field: string; message: string }

/** Erro da API em Problem Details (RFC 9457). */
export class ApiError extends Error {
  readonly status: number
  readonly type: string
  readonly title: string
  readonly errors: FieldError[]

  constructor(
    status: number,
    type: string,
    title: string,
    detail: string,
    errors: FieldError[] = [],
  ) {
    super(detail)
    this.name = "ApiError"
    this.status = status
    this.type = type
    this.title = title
    this.errors = errors
  }

  /** Último trecho do type: "https://festa.com/errors/slug-taken" → "slug-taken". */
  get code(): string {
    return this.type.split("/").pop() ?? ""
  }
}

const NETWORK_ERROR = () =>
  new ApiError(
    0,
    "network",
    "Sem conexão",
    "Não foi possível falar com o servidor. Confira sua internet e tente de novo.",
  )

/** Converte o corpo de erro da API em ApiError, tolerando respostas fora do padrão. */
export function toApiError(status: number, body: unknown): ApiError {
  if (body && typeof body === "object") {
    const problem = body as Record<string, unknown>
    const errors = Array.isArray(problem.errors)
      ? problem.errors.filter(
          (e): e is FieldError =>
            !!e &&
            typeof e === "object" &&
            typeof (e as FieldError).field === "string" &&
            typeof (e as FieldError).message === "string",
        )
      : []
    return new ApiError(
      status,
      typeof problem.type === "string" ? problem.type : "about:blank",
      typeof problem.title === "string" ? problem.title : "Erro",
      typeof problem.detail === "string"
        ? problem.detail
        : "Algo deu errado. Tente novamente.",
      errors,
    )
  }
  return new ApiError(
    status,
    "about:blank",
    "Erro",
    "Algo deu errado. Tente novamente.",
  )
}

export function readCookie(name: string): string | undefined {
  if (typeof document === "undefined") return undefined
  const prefix = `${name}=`
  const found = document.cookie
    .split("; ")
    .find((part) => part.startsWith(prefix))
  return found ? decodeURIComponent(found.slice(prefix.length)) : undefined
}

async function csrfToken(forceRefresh = false): Promise<string> {
  const current = forceRefresh ? undefined : readCookie("XSRF-TOKEN")
  if (current) return current
  try {
    await fetch(`${API_URL}/api/v1/auth/csrf`, { credentials: "include" })
  } catch {
    throw NETWORK_ERROR()
  }
  const token = readCookie("XSRF-TOKEN")
  if (!token) throw NETWORK_ERROR()
  return token
}

type RequestOptions = {
  method?: "GET" | "POST" | "PATCH" | "PUT" | "DELETE"
  body?: unknown
}

/** Faz a requisição; em escrita, envia o token CSRF e tenta de novo uma vez se ele tiver girado. */
export async function api<T>(
  path: string,
  { method = "GET", body }: RequestOptions = {},
): Promise<T> {
  const isWrite = method !== "GET"

  const send = async (forceNewToken: boolean) => {
    const headers: Record<string, string> = {}
    if (body !== undefined) headers["Content-Type"] = "application/json"
    if (isWrite) headers["X-XSRF-TOKEN"] = await csrfToken(forceNewToken)
    try {
      return await fetch(`${API_URL}${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
        credentials: "include",
      })
    } catch {
      throw NETWORK_ERROR()
    }
  }

  let response = await send(false)
  if (isWrite && response.status === 403) {
    const error = toApiError(403, await response.json().catch(() => null))
    if (error.code !== "csrf") throw error
    response = await send(true)
  }

  if (response.status === 204) return undefined as T
  const payload = await response.json().catch(() => null)
  if (!response.ok) throw toApiError(response.status, payload)
  return payload as T
}
