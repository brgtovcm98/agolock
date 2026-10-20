import { cva, type VariantProps } from 'class-variance-authority'
import type { ComponentProps } from 'react'
import { cn } from '@/shared/lib/cn'

export const badgeVariants = cva(
  'inline-flex h-7 shrink-0 items-center justify-center border-2 border-border px-2.5 text-xs leading-none font-bold whitespace-nowrap',
  {
    variants: {
      variant: {
        neutral: 'bg-card text-foreground',
        chipOn: 'bg-foreground text-background',
        use: 'bg-success text-on-accent',
        sto: 'bg-accent text-on-accent',
        dis: 'bg-sunken text-foreground line-through',
        info: 'bg-info text-on-info',
        override: 'bg-primary text-on-accent',
      },
    },
    defaultVariants: {
      variant: 'neutral',
    },
  },
)

type BadgeProps = ComponentProps<'span'> & VariantProps<typeof badgeVariants>

export function Badge({ className, variant, ...props }: BadgeProps) {
  return <span className={cn(badgeVariants({ variant }), className)} {...props} />
}
