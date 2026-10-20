import { Moon, Sun } from 'lucide-react'
import { useTheme } from '@/shared/theme/theme-provider'
import { cn } from '@/shared/lib/cn'

export function ThemeToggle({ className }: { className?: string }) {
  const { resolvedTheme, toggle } = useTheme()
  const isDark = resolvedTheme === 'dark'

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={isDark ? '라이트 모드로 전환' : '다크 모드로 전환'}
      title={isDark ? '라이트 모드' : '다크 모드'}
      className={cn(
        'border-border bg-card text-foreground shadow-brutal-sm grid size-11 place-items-center border-[2.5px] active:translate-x-[2px] active:translate-y-[2px] active:shadow-none',
        className,
      )}
    >
      {isDark ? (
        <Sun aria-hidden="true" className="size-5" strokeWidth={1.8} />
      ) : (
        <Moon aria-hidden="true" className="size-5" strokeWidth={1.8} />
      )}
    </button>
  )
}
