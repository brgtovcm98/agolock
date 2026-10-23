import { Link } from 'react-router-dom'
import type { NavigationItem } from '@/shared/navigation'
import { cn } from '@/shared/lib/cn'

export function NavList({
  items,
  active,
  className,
  compact = false,
}: {
  items: NavigationItem[]
  active: string
  className?: string
  compact?: boolean
}) {
  return (
    <nav className={cn('flex flex-col gap-2', className)} aria-label="주요 메뉴">
      {items.map((item) => {
        const Icon = item.icon
        const isActive = item.href === active
        return (
          <Link
            key={item.href}
            to={item.href}
            aria-current={isActive ? 'page' : undefined}
            className={cn(
              'flex h-11 items-center gap-3 border-[2.5px] px-3 text-sm font-black transition-colors duration-150',
              isActive
                ? 'border-border bg-primary text-on-accent shadow-brutal'
                : 'hover:bg-sunken border-transparent',
              compact && 'h-10 text-xs',
            )}
          >
            <Icon aria-hidden="true" className="size-[18px] shrink-0" strokeWidth={1.8} />
            <span className="truncate">{item.label}</span>
            {item.badge ? (
              <span className="border-border bg-info text-on-info ml-auto inline-flex h-6 items-center border-2 px-1.5 text-[10px] font-bold">
                {item.badge}
              </span>
            ) : null}
          </Link>
        )
      })}
    </nav>
  )
}
