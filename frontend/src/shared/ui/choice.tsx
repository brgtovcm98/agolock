import { Check } from 'lucide-react'
import type { ComponentProps, ReactNode } from 'react'
import { cn } from '@/shared/lib/cn'

type ChoiceProps = Omit<ComponentProps<'input'>, 'type' | 'children'> & {
  type: 'radio' | 'checkbox'
  children: ReactNode
  className?: string
}

/** 사각 라디오·체크박스. 입력 전체를 44px 이상 터치 영역으로 감싼다 (R-03, C-01). */
export function Choice({ type, className, children, ...props }: ChoiceProps) {
  return (
    <label className={cn('flex min-h-11 cursor-pointer items-center gap-3', className)}>
      <input type={type} className="peer sr-only" {...props} />
      <span
        aria-hidden="true"
        className="border-border bg-card text-on-accent peer-checked:bg-primary peer-focus-visible:outline-foreground grid size-[22px] shrink-0 place-items-center border-[2.5px] peer-focus-visible:outline-[3px] peer-focus-visible:outline-offset-[3px] peer-disabled:opacity-50 [&>svg]:opacity-0 peer-checked:[&>svg]:opacity-100"
      >
        <Check className="size-4" strokeWidth={3.2} />
      </span>
      {children}
    </label>
  )
}

export function RadioCard({
  className,
  children,
  ...props
}: Omit<ComponentProps<'input'>, 'type'> & { children: ReactNode }) {
  return (
    <Choice
      type="radio"
      className={cn(
        'border-border bg-card hover:bg-sunken has-[:checked]:bg-sunken min-h-14 border-[2.5px] p-3',
        className,
      )}
      {...props}
    >
      {children}
    </Choice>
  )
}
