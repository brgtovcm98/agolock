import { useEffect } from 'react'
import { useBlocker } from 'react-router-dom'
import { ConfirmDialog } from '@/shared/ui/confirm-dialog'
export function DirtyLeave({ dirty }: { dirty: boolean }) {
  const blocker = useBlocker(dirty)
  useEffect(() => {
    if (!dirty) return
    const beforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault()
      event.returnValue = ''
    }
    window.addEventListener('beforeunload', beforeUnload)
    return () => window.removeEventListener('beforeunload', beforeUnload)
  }, [dirty])
  return (
    <ConfirmDialog
      open={blocker.state === 'blocked'}
      onOpenChange={(open) => {
        if (!open && blocker.state === 'blocked') blocker.reset()
      }}
      title="입력을 취소할까요?"
      description="저장하지 않은 입력이 사라져요."
      confirmLabel="입력 취소하고 이동"
      onConfirm={() => {
        if (blocker.state === 'blocked') blocker.proceed()
      }}
    />
  )
}
