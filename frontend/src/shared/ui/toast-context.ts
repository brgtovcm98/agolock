import { createContext, useContext } from 'react'

export type ToastVariant = 'success' | 'error' | 'info'

export type ToastInput = {
  id?: string
  title: string
  description?: string
  variant?: ToastVariant
  duration?: number
  type?: 'foreground' | 'background'
}

export type ToastContextValue = {
  show: (input: ToastInput) => string
  dismiss: (id: string) => void
}

export const ToastContext = createContext<ToastContextValue | null>(null)

export function useToast() {
  const context = useContext(ToastContext)
  if (!context) {
    throw new Error('useToast는 ToastProvider 안에서 사용해야 합니다.')
  }
  return context
}
