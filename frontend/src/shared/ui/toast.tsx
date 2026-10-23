import * as ToastPrimitive from '@radix-ui/react-toast'
import { AlertTriangle, Check, Info, X } from 'lucide-react'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { ToastContext, type ToastInput, type ToastVariant } from '@/shared/ui/toast-context'

type ToastItem = {
  id: string
  title: string
  description?: string
  variant: ToastVariant
  duration: number
  type: 'foreground' | 'background'
}

const icons = {
  success: Check,
  error: AlertTriangle,
  info: Info,
} as const

function newToastId() {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `toast-${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function useCompactViewport() {
  const [compact, setCompact] = useState(() => {
    if (typeof window === 'undefined') return false
    return window.matchMedia('(max-width: 1023px)').matches
  })

  useEffect(() => {
    const media = window.matchMedia('(max-width: 1023px)')
    const listener = (event: MediaQueryListEvent) => setCompact(event.matches)
    media.addEventListener('change', listener)
    return () => media.removeEventListener('change', listener)
  }, [])

  return compact
}

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<ToastItem[]>([])
  const compact = useCompactViewport()

  const dismiss = useCallback((id: string) => {
    setToasts((current) => current.filter((toast) => toast.id !== id))
  }, [])

  const show = useCallback(
    (input: ToastInput) => {
      const id = input.id ?? newToastId()
      const item: ToastItem = {
        id,
        title: input.title,
        description: input.description,
        variant: input.variant ?? 'info',
        duration: input.duration ?? (input.variant === 'error' ? 8000 : 5000),
        type: input.type ?? (input.variant === 'error' ? 'foreground' : 'background'),
      }
      setToasts((current) => {
        const next = [...current.filter((toast) => toast.id !== id), item]
        return next.slice(-(compact ? 1 : 3))
      })
      return id
    },
    [compact],
  )

  const value = useMemo(() => ({ show, dismiss }), [dismiss, show])

  return (
    <ToastContext.Provider value={value}>
      <ToastPrimitive.Provider label="알림" swipeDirection="down" duration={5000}>
        {children}
        <ToastPrimitive.Viewport className="toast-viewport" label="알림 (F8)">
          {toasts.map((toast) => {
            const Icon = icons[toast.variant]
            return (
              <ToastPrimitive.Root
                key={toast.id}
                className={`toast-root toast-${toast.variant}`}
                duration={toast.duration}
                type={toast.type}
                onOpenChange={(open) => {
                  if (!open) dismiss(toast.id)
                }}
              >
                <div className="toast-icon" aria-hidden="true">
                  <Icon className="size-5" strokeWidth={2.2} />
                </div>
                <div className="min-w-0 flex-1 py-3 pr-1">
                  <ToastPrimitive.Title className="text-[13px] leading-tight font-black">
                    {toast.title}
                  </ToastPrimitive.Title>
                  {toast.description ? (
                    <ToastPrimitive.Description className="text-foreground/70 mt-1 text-xs leading-snug font-medium">
                      {toast.description}
                    </ToastPrimitive.Description>
                  ) : null}
                </div>
                <ToastPrimitive.Close
                  aria-label="알림 닫기"
                  className="border-border hover:bg-sunken grid size-11 shrink-0 place-items-center border-l-[2.5px]"
                >
                  <X aria-hidden="true" className="size-4" />
                </ToastPrimitive.Close>
              </ToastPrimitive.Root>
            )
          })}
        </ToastPrimitive.Viewport>
      </ToastPrimitive.Provider>
    </ToastContext.Provider>
  )
}
