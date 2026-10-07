import { NextResponse, type NextRequest } from "next/server"

/**
 * Checagem otimista: sem cookie de sessão, nem tenta renderizar o painel.
 * A verificação de verdade é feita no servidor pela DAL (src/lib/auth.ts).
 */
export function proxy(request: NextRequest) {
  if (!request.cookies.has("festa_session")) {
    const login = new URL("/entrar", request.url)
    login.searchParams.set("next", request.nextUrl.pathname)
    return NextResponse.redirect(login)
  }
  return NextResponse.next()
}

export const config = {
  matcher: ["/painel/:path*"],
}
