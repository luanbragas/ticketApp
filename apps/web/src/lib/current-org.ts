/** Organização escolhida no seletor do painel. Lida no servidor por getCurrentOrganization(). */
export function setCurrentOrganization(organizationId: string) {
  document.cookie = `festa_org=${encodeURIComponent(organizationId)}; path=/; max-age=31536000; samesite=lax`
}
