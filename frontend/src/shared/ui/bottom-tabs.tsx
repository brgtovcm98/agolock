import { Link } from 'react-router-dom'
import type { NavigationItem } from '@/shared/navigation'
import { cn } from '@/shared/lib/cn'

export function BottomTabs({
  items,
  active,
  className,
}: {
  items: NavigationItem[]
  active: string
  className?: string
}) {
  return (
    <nav
      aria-label="모바일 주요 메뉴"
      className={cn(
        'safe-bottom border-border bg-card fixed inset-x-0 bottom-0 z-40 grid grid-cols-5 border-t-[2.5px] lg:hidden',
        className,
      )}
    >
      {items.map((item) => {
        const Icon = item.icon
        const isActive = item.href === active
        return (
          <Link
            key={item.href}
            to={item.href}
            aria-current={isActive ? 'page' : undefined}
            className={cn(
              'border-border flex min-h-14 flex-col items-center justify-center gap-1 border-r-[1.5px] px-1 py-2 text-[11px] leading-none font-black transition-colors duration-150 last:border-r-0',
              isActive ? 'bg-primary text-on-accent' : 'bg-card text-foreground',
            )}
          >
            <Icon aria-hidden="true" className="size-[18px]" strokeWidth={1.8} />
            <span>{item.label}</span>
          </Link>
        )
      })}
    </nav>
  )
}
