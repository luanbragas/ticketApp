"use client"

import { useMutation } from "@tanstack/react-query"
import {
  CheckIcon,
  ChevronsUpDownIcon,
  HouseIcon,
  LogOutIcon,
  PlusIcon,
  UsersIcon,
  type LucideIcon,
} from "lucide-react"
import Link from "next/link"
import { usePathname, useRouter } from "next/navigation"

import { Logo } from "@/components/brand/logo"
import { Button } from "@/components/ui/button"
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { logout } from "@/lib/api/auth"
import { ROLE_LABELS, type Me, type Organization } from "@/lib/api/types"
import { setCurrentOrganization } from "@/lib/current-org"
import { cn } from "@/lib/utils"
import { useSessionChanged } from "@/lib/session"

type NavItem = { href: string; label: string; icon: LucideIcon }

const NAV: NavItem[] = [
  { href: "/painel", label: "Início", icon: HouseIcon },
  { href: "/painel/equipe", label: "Equipe", icon: UsersIcon },
]

function isActive(pathname: string, href: string) {
  return href === "/painel" ? pathname === href : pathname.startsWith(href)
}

/** Shell do painel: menu lateral no desktop, barra inferior no celular (FRONTEND.md). */
export function PanelShell({
  user,
  organizations,
  current,
  children,
}: {
  user: Me
  organizations: Organization[]
  current: Organization | null
  children: React.ReactNode
}) {
  const pathname = usePathname()

  return (
    <div className="flex min-h-dvh flex-1 bg-muted/40">
      <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col border-r bg-background md:flex">
        <div className="px-5 py-5">
          <Logo href="/painel" />
        </div>
        <div className="px-3">
          <OrganizationSwitcher
            organizations={organizations}
            current={current}
          />
        </div>
        <nav aria-label="Painel" className="mt-4 grid gap-1 px-3">
          {NAV.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              aria-current={isActive(pathname, item.href) ? "page" : undefined}
              className={cn(
                "flex h-10 items-center gap-3 rounded-md px-3 text-sm font-medium text-muted-foreground transition-colors hover:bg-muted hover:text-foreground",
                isActive(pathname, item.href) && "bg-muted text-foreground",
              )}
            >
              <item.icon className="size-4" aria-hidden />
              {item.label}
            </Link>
          ))}
        </nav>
        <div className="mt-auto border-t p-3">
          <UserMenu user={user} />
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-14 items-center gap-3 border-b bg-background px-4 md:hidden">
          <Logo href="/painel" />
          <div className="ml-auto flex min-w-0 items-center gap-1">
            <OrganizationSwitcher
              organizations={organizations}
              current={current}
              compact
            />
            <UserMenu user={user} compact />
          </div>
        </header>

        <main className="mx-auto w-full max-w-5xl flex-1 px-4 pt-6 pb-28 md:px-8 md:pt-10 md:pb-10">
          {children}
        </main>

        <nav
          aria-label="Painel"
          className="fixed inset-x-0 bottom-0 z-20 grid grid-cols-2 border-t bg-background pb-[env(safe-area-inset-bottom)] md:hidden"
        >
          {NAV.map((item) => (
            <Link
              key={item.href}
              href={item.href}
              aria-current={isActive(pathname, item.href) ? "page" : undefined}
              className={cn(
                "flex h-16 flex-col items-center justify-center gap-1 text-xs font-medium text-muted-foreground",
                isActive(pathname, item.href) && "text-primary",
              )}
            >
              <item.icon className="size-5" aria-hidden />
              {item.label}
            </Link>
          ))}
        </nav>
      </div>
    </div>
  )
}

function OrganizationSwitcher({
  organizations,
  current,
  compact = false,
}: {
  organizations: Organization[]
  current: Organization | null
  compact?: boolean
}) {
  const router = useRouter()

  const choose = (organization: Organization) => {
    setCurrentOrganization(organization.id)
    router.refresh()
  }

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant={compact ? "ghost" : "outline"}
          className={cn(
            "h-11 justify-between gap-2",
            compact ? "max-w-44 px-2" : "w-full",
          )}
          aria-label="Trocar de organização"
        >
          <span className="min-w-0 truncate text-left">
            {current ? current.name : "Nenhuma organização"}
          </span>
          <ChevronsUpDownIcon
            className="size-4 shrink-0 opacity-60"
            aria-hidden
          />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align={compact ? "end" : "start"} className="w-64">
        {organizations.length > 0 && (
          <>
            <DropdownMenuLabel>Suas organizações</DropdownMenuLabel>
            {organizations.map((organization) => (
              <DropdownMenuItem
                key={organization.id}
                onSelect={() => choose(organization)}
                className="min-h-10"
              >
                <span className="min-w-0 flex-1">
                  <span className="block truncate">{organization.name}</span>
                  <span className="block text-xs text-muted-foreground">
                    {ROLE_LABELS[organization.role]}
                  </span>
                </span>
                {organization.id === current?.id && (
                  <CheckIcon className="size-4" aria-hidden />
                )}
              </DropdownMenuItem>
            ))}
            <DropdownMenuSeparator />
          </>
        )}
        <DropdownMenuItem asChild className="min-h-10">
          <Link href="/painel/organizacoes/nova">
            <PlusIcon className="size-4" aria-hidden />
            Criar organização
          </Link>
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}

function UserMenu({ user, compact = false }: { user: Me; compact?: boolean }) {
  const router = useRouter()
  const onSessionChanged = useSessionChanged()
  const signOut = useMutation({
    mutationFn: logout,
    onSettled: () => {
      onSessionChanged()
      router.replace("/entrar")
      router.refresh()
    },
  })
  const displayName = user.name ?? user.email
  const initial = displayName.charAt(0).toUpperCase()

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          className={cn(
            "h-11 gap-3",
            compact ? "w-11 px-0" : "w-full justify-start px-2",
          )}
          aria-label="Menu da conta"
        >
          <span
            aria-hidden
            className="flex size-8 shrink-0 items-center justify-center rounded-full bg-primary text-sm font-semibold text-primary-foreground"
          >
            {initial}
          </span>
          {!compact && (
            <span className="min-w-0 text-left">
              <span className="block truncate text-sm font-medium">
                {displayName}
              </span>
              {user.name && (
                <span className="block truncate text-xs text-muted-foreground">
                  {user.email}
                </span>
              )}
            </span>
          )}
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-56">
        <DropdownMenuLabel className="truncate font-normal text-muted-foreground">
          {user.email}
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuItem
          onSelect={() => signOut.mutate()}
          disabled={signOut.isPending}
          className="min-h-10"
        >
          <LogOutIcon className="size-4" aria-hidden />
          Sair
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  )
}
