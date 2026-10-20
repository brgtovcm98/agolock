import type { ComponentProps } from 'react'
import { cn } from '@/shared/lib/cn'

export function Card({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('surface', className)} {...props} />
}

export function CardBody({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('flex flex-col gap-3 p-3', className)} {...props} />
}

export function CardRow({ className, ...props }: ComponentProps<'div'>) {
  return <div className={cn('flex items-center gap-3 p-3', className)} {...props} />
}
