import type { ReactNode } from 'react'
import { Home, LayoutGrid, List, Plus, Settings, Share2 } from 'lucide-react'
import { Avatar } from '@/shared/ui/avatar'
import { Badge } from '@/shared/ui/badge'
import { Brand } from '@/shared/ui/brand'
import { Button } from '@/shared/ui/button'
import { SearchField } from '@/shared/ui/search-field'
import { ThemeToggle } from '@/shared/ui/theme-toggle'
import { cn } from '@/shared/lib/cn'

const previewNav = [
  { label: '홈', href: 'home', icon: Home },
  { label: '공간', href: 'space', icon: LayoutGrid },
  { label: '재고', href: 'inventory', icon: List },
  { label: '공유', href: 'sharing', icon: Share2 },
  { label: '설정', href: 'settings', icon: Settings },
] as const

type PreviewScreen = (typeof previewNav)[number]['href']

export function PreviewShell({
  mode,
  active,
  searchValue = '',
  children,
}: {
  mode: 'mobile' | 'desktop'
  active: PreviewScreen
  searchValue?: string
  children: ReactNode
}) {
  if (mode === 'mobile') {
    return (
      <div className="border-border bg-background mx-auto flex min-h-[844px] w-[390px] flex-col border-4">
        <div className="flex flex-1 flex-col gap-4 p-4">{children}</div>
        <PreviewBottomTabs active={active} />
      </div>
    )
  }

  return (
    <div className="border-border bg-background flex min-h-[860px] w-[1280px] border-4">
      <PreviewSidebar active={active} />
      <div className="flex min-w-0 flex-1 flex-col">
        <PreviewTopBar searchValue={searchValue} />
        <main className="flex flex-1 flex-col gap-6 p-8">{children}</main>
      </div>
    </div>
  )
}

export function PreviewMobileHeader({
  variant = 'brand',
  spaceLabel = '내 Space',
}: {
  variant?: 'brand' | 'space'
  spaceLabel?: string
}) {
  return (
    <div className="flex items-center justify-between gap-3">
      <Brand label={variant === 'space' ? 'SPACE' : 'AGOLOCK'} />
      {variant === 'space' ? (
        <button
          type="button"
          className="bg-foreground text-background inline-flex h-9 items-center gap-1 px-3 text-xs font-black"
        >
          {spaceLabel}
          <span aria-hidden="true">▾</span>
        </button>
      ) : (
        <Avatar name="지" />
      )}
    </div>
  )
}

function PreviewSidebar({ active }: { active: PreviewScreen }) {
  return (
    <aside className="border-border bg-card flex w-[248px] shrink-0 flex-col gap-6 border-r-[2.5px] px-4 py-6">
      <Brand />
      <div className="flex flex-col gap-2">
        <p className="label-caps">SPACE</p>
        <div className="border-border bg-primary text-on-accent shadow-brutal flex h-11 items-center gap-3 border-[2.5px] px-3 text-sm font-black">
          <span aria-hidden="true" className="size-3 border-2 border-current" />내 Space
        </div>
        <div className="flex h-10 items-center gap-3 border-[2.5px] border-transparent px-3 text-xs font-bold">
          <span aria-hidden="true" className="size-3 border-2 border-current" />
          민지네 베란다
          <Badge variant="info" className="ml-auto h-6 px-1.5 text-[10px]">
            공유
          </Badge>
        </div>
      </div>

      <div className="flex flex-col gap-2">
        <p className="label-caps">MENU</p>
        <nav className="flex flex-col gap-2" aria-label="미리보기 메뉴">
          {previewNav.map((item) => {
            const Icon = item.icon
            const isActive = item.href === active
            return (
              <div
                key={item.href}
                aria-current={isActive ? 'page' : undefined}
                className={cn(
                  'flex h-11 items-center gap-3 border-[2.5px] px-3 text-sm font-black',
                  isActive
                    ? 'border-border bg-primary text-on-accent shadow-brutal'
                    : 'border-transparent',
                )}
              >
                <Icon aria-hidden="true" className="size-[18px]" strokeWidth={1.8} />
                {item.label}
              </div>
            )
          })}
        </nav>
      </div>

      <div className="border-border mt-auto flex items-center gap-3 border-t-[2.5px] pt-4">
        <Avatar name="지" />
        <div>
          <p className="text-xs font-black">지민</p>
          <p className="text-foreground/70 text-[11px]">디자인 미리보기</p>
        </div>
        <ThemeToggle className="ml-auto size-10 shadow-none" />
      </div>
    </aside>
  )
}

function PreviewTopBar({ searchValue }: { searchValue: string }) {
  return (
    <header className="border-border flex items-center gap-4 border-b-[2.5px] px-8 py-4">
      <SearchField
        value={searchValue}
        placeholder="물건 검색"
        readOnly
        className="max-w-[640px] flex-1"
      />
      <Button className="ml-auto shrink-0">
        <Plus aria-hidden="true" className="size-4" />
        재고 추가
      </Button>
      <Avatar name="지" />
    </header>
  )
}

function PreviewBottomTabs({ active }: { active: PreviewScreen }) {
  return (
    <nav
      aria-label="미리보기 모바일 메뉴"
      className="safe-bottom border-border bg-card grid grid-cols-5 border-t-[2.5px]"
    >
      {previewNav.map((item) => {
        const Icon = item.icon
        const isActive = item.href === active
        return (
          <div
            key={item.href}
            aria-current={isActive ? 'page' : undefined}
            className={cn(
              'border-border flex min-h-14 flex-col items-center justify-center gap-1 border-r-[1.5px] py-2 text-[11px] leading-none font-black last:border-r-0',
              isActive ? 'bg-primary text-on-accent' : 'bg-card text-foreground',
            )}
          >
            <Icon aria-hidden="true" className="size-[18px]" strokeWidth={1.8} />
            {item.label}
          </div>
        )
      })}
    </nav>
  )
}
