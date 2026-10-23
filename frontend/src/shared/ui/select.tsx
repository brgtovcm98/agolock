import { ChevronDown } from 'lucide-react'
import type { ComponentProps } from 'react'
import { cn } from '@/shared/lib/cn'

type SelectProps = ComponentProps<'select'> & {
  compact?: boolean
  wrapperClassName?: string
}

export function Select({ className, wrapperClassName, compact = false, ...props }: SelectProps) {
  return (
    <span className={cn('relative inline-block max-w-full', wrapperClassName)}>
      <select
        className={cn(
          'peer border-border bg-card text-foreground focus-visible:outline-foreground h-12 w-full appearance-none border-[2.5px] pr-12 pl-3 text-sm font-medium focus-visible:outline-[3px] focus-visible:outline-offset-[3px]',
          'disabled:bg-sunken disabled:text-foreground/70 disabled:border-dashed',
          compact && 'h-11 text-xs font-black',
          className,
        )}
        {...props}
      />
      <span
        aria-hidden="true"
        className="bg-accent text-on-accent border-border pointer-events-none absolute inset-y-0 right-0 grid w-10 place-items-center border-l-[2.5px] peer-disabled:border-dashed peer-disabled:bg-transparent peer-disabled:text-current"
      >
        <ChevronDown className="size-4" strokeWidth={2.4} />
      </span>
    </span>
  )
}
