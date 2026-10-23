import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'
import type { ComponentProps } from 'react'
import { cn } from '@/shared/lib/cn'

export const buttonVariants = cva(
  [
    'inline-flex items-center justify-center gap-2 border-[2.5px] border-border font-black whitespace-nowrap',
    'shadow-brutal-sm transition-[translate,box-shadow,background-color]',
    'duration-[var(--motion-press)] ease-[var(--ease-snap)]',
    'hover:-translate-x-px hover:-translate-y-px hover:shadow-[5px_5px_0_var(--shadow-color)]',
    'active:translate-x-[2px] active:translate-y-[2px] active:shadow-[2px_2px_0_var(--shadow-color)]',
    'focus-visible:outline-[3px] focus-visible:outline-offset-[3px] focus-visible:outline-foreground',
    'disabled:bg-sunken disabled:text-foreground/70 disabled:border-dashed disabled:pointer-events-none disabled:translate-x-0 disabled:translate-y-0 disabled:shadow-none',
    'motion-reduce:transition-none motion-reduce:hover:translate-x-0 motion-reduce:hover:translate-y-0 motion-reduce:active:translate-x-0 motion-reduce:active:translate-y-0',
  ].join(' '),
  {
    variants: {
      variant: {
        primary: 'bg-primary text-on-accent',
        secondary: 'bg-card text-foreground hover:bg-sunken',
        invert: 'bg-foreground text-background hover:opacity-90',
        ghost:
          'border-transparent bg-transparent text-foreground shadow-none hover:translate-x-0 hover:translate-y-0 hover:bg-sunken hover:shadow-none active:translate-x-px active:translate-y-px active:shadow-none',
      },
      size: {
        sm: 'h-[var(--control-sm)] px-3 text-xs',
        default: 'h-[var(--control-md)] px-4 text-sm',
        lg: 'h-[var(--control-lg)] px-6 text-base',
        icon: 'size-11 p-0',
      },
    },
    defaultVariants: {
      variant: 'primary',
      size: 'default',
    },
  },
)

type ButtonProps = ComponentProps<'button'> &
  VariantProps<typeof buttonVariants> & {
    asChild?: boolean
  }

export function Button({ className, variant, size, asChild = false, type, ...props }: ButtonProps) {
  const Component = asChild ? Slot : 'button'
  return (
    <Component
      className={cn(buttonVariants({ variant, size }), className)}
      type={asChild ? undefined : (type ?? 'button')}
      {...props}
    />
  )
}
