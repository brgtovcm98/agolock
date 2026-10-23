import * as AlertDialog from '@radix-ui/react-alert-dialog'
import type { ReactNode } from 'react'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'

export function ConfirmDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel = '확인',
  pending = false,
  error,
  onConfirm,
  destructive = false,
  children,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: ReactNode
  confirmLabel?: string
  pending?: boolean
  error?: string | null
  onConfirm: () => void
  destructive?: boolean
  children?: ReactNode
}) {
  return (
    <AlertDialog.Root open={open} onOpenChange={onOpenChange}>
      <AlertDialog.Portal>
        <AlertDialog.Overlay className="dialog-overlay bg-foreground/40 fixed inset-0 z-50" />
        <AlertDialog.Content className="dialog-content border-border bg-background shadow-brutal fixed top-1/2 left-1/2 z-50 w-[min(92vw,420px)] -translate-x-1/2 -translate-y-1/2 border-[2.5px] p-5">
          <AlertDialog.Title className="text-xl font-black">{title}</AlertDialog.Title>
          {description ? (
            <AlertDialog.Description asChild>
              <div className="text-foreground/70 mt-2 text-xs">{description}</div>
            </AlertDialog.Description>
          ) : null}
          {children}
          {error ? (
            <Alert variant="error" className="mt-3">
              {error}
            </Alert>
          ) : null}
          <div className="mt-5 flex justify-end gap-2">
            <AlertDialog.Cancel asChild>
              <Button type="button" variant="secondary">
                취소
              </Button>
            </AlertDialog.Cancel>
            <Button
              type="button"
              onClick={onConfirm}
              disabled={pending}
              aria-busy={pending}
              className={destructive ? 'border-danger text-danger' : undefined}
              variant={destructive ? 'secondary' : 'primary'}
            >
              {pending ? '처리 중…' : confirmLabel}
            </Button>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  )
}
