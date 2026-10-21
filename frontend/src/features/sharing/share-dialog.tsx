import * as Dialog from '@radix-ui/react-dialog'
import { useMutation } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { useState } from 'react'
import { sharingApi, type SharePermission } from './api'
import { ApiError } from '@/shared/api/client'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { TextField } from '@/shared/ui/text-field'
import { permissionDescription, permissionLabel } from '@/shared/lib/permission'

export function ShareDialog({
  target,
  onClose,
}: {
  target: { roomId?: string; shelfId?: string; name: string }
  onClose: () => void
}) {
  const [email, setEmail] = useState('')
  const [permission, setPermission] = useState<SharePermission>('VIEW')
  const [error, setError] = useState<string | null>(null)
  const [sent, setSent] = useState(false)

  const mutation = useMutation({
    mutationFn: () =>
      sharingApi.createInvitation({
        roomId: target.roomId,
        shelfId: target.shelfId,
        invitedEmail: email.trim(),
        permission,
      }),
    onSuccess: () => {
      setSent(true)
      setError(null)
    },
    onError: (cause) =>
      setError(cause instanceof ApiError ? cause.message : '초대를 보내지 못했습니다.'),
  })

  return (
    <Dialog.Root open onOpenChange={(next) => !next && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="bg-foreground/40 fixed inset-0 z-50" />
        <Dialog.Content className="border-border bg-background shadow-brutal fixed top-1/2 left-1/2 z-50 w-[min(92vw,460px)] -translate-x-1/2 -translate-y-1/2 border-[2.5px] p-5">
          <div className="flex items-start justify-between gap-3">
            <div className="flex flex-col gap-1">
              <Dialog.Title className="text-xl font-black">공유 초대</Dialog.Title>
              <Dialog.Description className="text-foreground/60 text-xs">
                {target.name}에 초대할 이메일과 권한을 선택하세요.
              </Dialog.Description>
            </div>
            <Dialog.Close asChild>
              <button
                type="button"
                aria-label="닫기"
                className="border-border bg-card grid size-9 shrink-0 place-items-center border-[2.5px]"
              >
                <X aria-hidden="true" className="size-4" />
              </button>
            </Dialog.Close>
          </div>

          {sent ? (
            <div className="mt-4 flex flex-col gap-3">
              <Alert variant="success">
                초대를 보냈어요. 상대가 수락하면 공유가 시작됩니다.
              </Alert>
              <Button type="button" onClick={onClose}>
                닫기
              </Button>
            </div>
          ) : (
            <form
              className="mt-4 flex flex-col gap-3"
              onSubmit={(event) => {
                event.preventDefault()
                if (email.trim()) mutation.mutate()
              }}
            >
              {error ? <Alert variant="error">{error}</Alert> : null}
              <TextField
                label="이메일"
                type="email"
                value={email}
                autoComplete="email"
                onChange={(event) => setEmail(event.target.value)}
                autoFocus
              />
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-black">권한</span>
                <select
                  aria-label="공유 권한"
                  value={permission}
                  onChange={(event) => setPermission(event.target.value as SharePermission)}
                  className="border-border bg-card h-12 border-[2.5px] px-3 text-sm"
                >
                  <option value="VIEW">{permissionLabel('VIEW')}</option>
                  <option value="EDIT">{permissionLabel('EDIT')}</option>
                  <option value="MANAGE">{permissionLabel('MANAGE')}</option>
                </select>
                <span className="text-foreground/60 text-[11px]">
                  {permissionDescription(permission)}
                </span>
              </label>
              <div className="flex justify-end gap-2">
                <Button type="button" variant="secondary" onClick={onClose}>
                  취소
                </Button>
                <Button type="submit" disabled={!email.trim() || mutation.isPending}>
                  {mutation.isPending ? '보내는 중…' : '초대 보내기'}
                </Button>
              </div>
            </form>
          )}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
