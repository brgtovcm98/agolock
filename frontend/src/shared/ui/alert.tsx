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
  action,
  className,
}: {
  variant?: AlertVariant
  children: ReactNode
  /** 오류에서 복구할 수 있는 동작(예: 다시 시도) */
  action?: ReactNode
  className?: string
}) {
  const Icon = variant === 'error' ? AlertTriangle : variant === 'success' ? CheckCircle2 : Info
  return (
    <div
      role={variant === 'error' ? 'alert' : 'status'}
      className={cn(
        'bg-card flex flex-wrap items-start gap-2 border-[2.5px] px-3 py-2 text-xs font-bold',
        styles[variant],
        className,
      )}
    >
      <span className="grid h-4 shrink-0 place-items-center">
        <Icon aria-hidden="true" className="size-4" strokeWidth={1.8} />
      </span>
      <div className="min-w-0 flex-1">{children}</div>
      {action}
    </div>
  )
}
