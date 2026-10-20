import type { ReactNode } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { activeNavigationHref, mainNavigation } from '@/shared/navigation'
import { Avatar } from '@/shared/ui/avatar'
import { BottomTabs } from '@/shared/ui/bottom-tabs'
import { Brand } from '@/shared/ui/brand'
import { NavList } from '@/shared/ui/nav-list'
import { ThemeToggle } from '@/shared/ui/theme-toggle'

export function AppShell({ children }: { children: ReactNode }) {
  const location = useLocation()
  const active = activeNavigationHref(location.pathname)

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
              <span aria-hidden="true" className="size-3 border-2 border-current" />내 Space
            </Link>
            <Link
              to="/space"
              className="hover:bg-sunken flex h-10 items-center gap-3 border-[2.5px] border-transparent px-3 text-xs font-bold"
            >
              <span aria-hidden="true" className="size-3 border-2 border-current" />
              민지네 베란다
              <span className="border-border bg-info text-on-info ml-auto inline-flex h-6 items-center border-2 px-1.5 text-[10px] font-bold">
                공유
              </span>
            </Link>
          </div>
        </div>

        <div className="flex flex-col gap-2">
          <p className="label-caps text-foreground">MENU</p>
          <NavList items={mainNavigation} active={active} />
        </div>

        <div className="border-border mt-auto flex items-center gap-3 border-t-[2.5px] pt-4">
          <Avatar name="지" />
          <div className="min-w-0">
            <p className="truncate text-xs font-black">지민</p>
            <p className="text-foreground/70 truncate text-[11px]">M0 기반 부트스트랩</p>
          </div>
          <ThemeToggle className="ml-auto size-10 shadow-none" />
        </div>
      </aside>

      <div className="lg:pl-[248px]">
        <header className="border-border bg-background sticky top-0 z-30 flex h-16 items-center justify-between border-b-[2.5px] px-4 lg:px-8">
          <Brand className="lg:hidden" />
          <div className="hidden items-center gap-3 lg:flex">
            <span className="label-caps text-foreground/70">M0 FOUNDATION</span>
            <span className="border-border bg-accent text-on-accent inline-flex h-7 items-center border-2 px-2 text-xs font-bold">
              API 연결 전
            </span>
          </div>
          <div className="flex items-center gap-3">
            <ThemeToggle className="lg:hidden" />
            <Avatar name="지" className="hidden lg:grid" />
          </div>
        </header>

        <main className="mx-auto w-full max-w-5xl px-4 pt-6 pb-28 lg:px-8 lg:pt-8 lg:pb-10">
          {children}
        </main>
      </div>

      <BottomTabs items={mainNavigation} active={active} />
    </div>
  )
}
