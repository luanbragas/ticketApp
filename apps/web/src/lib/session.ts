"use client"

import { useQueryClient } from "@tanstack/react-query"
import { useCallback } from "react"

/**
 * Chame quando a conta logada mudar (login, cadastro, link mágico, logout): descarta
 * tudo o que foi buscado em nome da conta anterior.
 */
export function useSessionChanged() {
  const queryClient = useQueryClient()
  return useCallback(() => queryClient.clear(), [queryClient])
}
