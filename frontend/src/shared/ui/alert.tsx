import type { ReactNode } from 'react'
import { AlertTriangle, CheckCircle2, Info } from 'lucide-react'
import { cn } from '@/shared/lib/cn'

type AlertVariant = 'error' | 'info' | 'success'

const styles: Record<AlertVariant, string> = {
  error: 'border-danger text-danger',
  info: 'border-border text-foreground',
  success: 'border-success text-foreground',
}

export function Alert({
  variant = 'info',
  children,
  className,
}: {
  variant?: AlertVariant
  children: ReactNode
  className?: string
}) {
  const Icon = variant === 'error' ? AlertTriangle : variant === 'success' ? CheckCircle2 : Info
  return (
    <div
      role={variant === 'error' ? 'alert' : 'status'}
      className={cn(
        'bg-card flex items-start gap-2 border-[2.5px] px-3 py-2.5 text-xs font-bold',
        styles[variant],
        className,
      )}
    >
      <Icon aria-hidden="true" className="mt-0.5 size-4 shrink-0" strokeWidth={1.8} />
      <div className="min-w-0">{children}</div>
    </div>
  )
}
