import type { ComponentProps, ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

export function SectionTitle({
  children,
  aside,
  className,
  as: Component = 'h2',
}: {
  children: ReactNode
  aside?: ReactNode
  className?: string
  as?: 'h2' | 'h3'
}) {
  return (
    <Component
      className={cn(
        'border-border flex items-center justify-between gap-3 border-b-[2.5px] pb-2 text-xs font-black tracking-[0.08em]',
        className,
      )}
    >
      <span>{children}</span>
      {aside ? <span className="font-bold tracking-normal">{aside}</span> : null}
    </Component>
  )
}

export function PageTitle({ className, ...props }: ComponentProps<'h1'>) {
  return (
    <h1
      className={cn(
        'text-[34px] leading-[1.05] font-black tracking-[-0.04em] lg:text-3xl',
        className,
      )}
      {...props}
    />
  )
}

export function MetaText({ className, ...props }: ComponentProps<'p'>) {
  return <p className={cn('text-foreground/80 text-xs font-medium', className)} {...props} />
}
