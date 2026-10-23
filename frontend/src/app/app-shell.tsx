import type { ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Plus } from 'lucide-react'
import { useAuth } from '@/features/auth/auth-provider'
import { AddInventoryProvider } from '@/features/inventory/add-inventory-provider'
import { useAddInventory } from '@/features/inventory/add-inventory-context'
import { activeNavigationHref, mainNavigation } from '@/shared/navigation'
import { cn } from '@/shared/lib/cn'
import { Avatar } from '@/shared/ui/avatar'
import { BottomTabs } from '@/shared/ui/bottom-tabs'
import { Brand } from '@/shared/ui/brand'
import { Button } from '@/shared/ui/button'
import { NavList } from '@/shared/ui/nav-list'
import { ThemeToggle } from '@/shared/ui/theme-toggle'

export function AppShell({ children }: { children: ReactNode }) {
  return (
    <AddInventoryProvider>
      <AppShellFrame>{children}</AppShellFrame>
    </AddInventoryProvider>
  )
}

function AppShellFrame({ children }: { children: ReactNode }) {
  const location = useLocation()
  const { user, space } = useAuth()
  const { openAdd } = useAddInventory()
  const active = activeNavigationHref(location.pathname)
  const hideAddFab =
    /^\/(inventory\/.+|items\/.+|space\/(rooms|shelves|boxes)\/.+|search|sharing|invitations|settings)(\/|$)/.test(
      location.pathname,
    )
  const showAddFab = !hideAddFab

  return (
    <div className="bg-background min-h-dvh">
      <aside
        aria-label="사이드바"
        className="border-border bg-card fixed inset-y-0 left-0 z-40 hidden w-[248px] flex-col gap-6 border-r-[2.5px] px-4 py-6 lg:flex"
      >
        <Brand />
        <div className="flex flex-col gap-2">
          <p className="label-caps text-foreground">SPACE</p>
          <div className="flex flex-col gap-2">
            <Link
              to="/"
              className="border-border bg-primary text-on-accent shadow-brutal flex h-11 items-center gap-3 border-[2.5px] px-3 text-sm font-black"
            >
              <span aria-hidden="true" className="size-3 border-2 border-current" />
              <span className="truncate">{space?.name ?? '내 Space'}</span>
            </Link>
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <p className="label-caps text-foreground">MENU</p>
          <NavList items={mainNavigation} active={active} />
        </div>

        <div className="border-border mt-auto flex items-center gap-3 border-t-[2.5px] pt-4">
          <Avatar name={user?.displayName ?? '?'} />
          <div className="min-w-0">
            <p className="truncate text-xs font-black">{user?.displayName ?? '사용자'}</p>
            <p className="text-foreground/70 truncate text-[11px]">{user?.email}</p>
          </div>
          <ThemeToggle className="ml-auto size-10 shadow-none" />
        </div>
      </aside>

      <div className="lg:pl-[248px]">
        <header className="border-border bg-background sticky top-0 z-30 flex h-16 items-center justify-between border-b-[2.5px] px-4 lg:px-8">
          <Brand className="lg:hidden" />
          <div className="ml-auto flex items-center gap-3">
            <Button
              type="button"
              size="sm"
              onClick={() => openAdd()}
              className="hidden lg:inline-flex"
            >
              <Plus aria-hidden="true" className="size-4" />
              등록
            </Button>
            <ThemeToggle className="lg:hidden" />
            <Avatar name={user?.displayName ?? '?'} className="hidden lg:grid" />
          </div>
        </header>

        <main
          className={cn(
            'mx-auto w-full max-w-5xl px-4 pt-6 lg:px-8 lg:pt-8 lg:pb-10',
            showAddFab ? 'pb-40' : 'pb-24',
          )}
        >
          {children}
        </main>
      </div>

      {showAddFab ? (
        <Button
          type="button"
          size="lg"
          onClick={() => openAdd()}
          aria-label="물건 등록"
          data-add-fab
          className="shadow-brutal hover:shadow-brutal-lg fixed right-4 bottom-20 z-40 active:translate-x-[4px] active:translate-y-[4px] active:shadow-none motion-reduce:active:translate-x-0 motion-reduce:active:translate-y-0 lg:hidden"
        >
          <Plus aria-hidden="true" className="size-5" />
          등록
        </Button>
      ) : null}

      <BottomTabs items={mainNavigation} active={active} />
    </div>
  )
}
