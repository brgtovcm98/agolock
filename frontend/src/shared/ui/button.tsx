import { Slot } from '@radix-ui/react-slot'
import { cva, type VariantProps } from 'class-variance-authority'
import type { ComponentProps } from 'react'
import { cn } from '@/shared/lib/cn'

export const buttonVariants = cva(
  'inline-flex items-center justify-center gap-2 border-[2.5px] border-border font-black transition-[transform,box-shadow,background-color] duration-100 active:translate-x-[2px] active:translate-y-[2px] active:shadow-none disabled:pointer-events-none disabled:opacity-50',
  {
    variants: {
      variant: {
        primary: 'bg-primary text-on-accent shadow-brutal-sm hover:brightness-105',
        secondary: 'bg-card text-foreground shadow-brutal-sm hover:bg-sunken',
        invert: 'bg-foreground text-background shadow-brutal-sm hover:opacity-90',
        ghost: 'border-transparent bg-transparent text-foreground hover:bg-sunken',
      },
      size: {
        sm: 'h-9 px-3 text-xs',
        default: 'h-12 px-4 text-sm',
        lg: 'h-14 px-6 text-base',
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
