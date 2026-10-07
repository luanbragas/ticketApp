import { api } from "./client"
import type { Me } from "./types"

export const signup = (body: {
  name: string
  email: string
  password: string
}) => api<Me>("/api/v1/auth/signup", { method: "POST", body })

export const login = (body: { email: string; password: string }) =>
  api<Me>("/api/v1/auth/login", { method: "POST", body })

export const logout = () => api<void>("/api/v1/auth/logout", { method: "POST" })

export const getMe = () => api<Me>("/api/v1/auth/me")

export const requestMagicLink = (email: string) =>
  api<void>("/api/v1/public/magic-links", { method: "POST", body: { email } })

export const consumeMagicLink = (token: string) =>
  api<Me>("/api/v1/auth/magic-link/consume", {
    method: "POST",
    body: { token },
  })
