import { Link } from 'react-router-dom'
import { cn } from '@/shared/lib/cn'

export function Brand({
  label = 'AGOLOCK',
  to = '/',
  className,
}: {
  label?: string
  to?: string
  className?: string
}) {
  return (
    <Link
      to={to}
      className={cn(
        'bg-foreground text-background inline-flex px-2 py-1 text-[15px] leading-none font-black tracking-[0.06em]',
        className,
      )}
      aria-label="AGO Lock 홈"
    >
      {label}
    </Link>
  )
}
