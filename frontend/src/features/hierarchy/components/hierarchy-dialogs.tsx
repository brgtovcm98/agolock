import * as AlertDialog from '@radix-ui/react-alert-dialog'
import * as Dialog from '@radix-ui/react-dialog'
import { X } from 'lucide-react'
import { useState, type FormEvent, type ReactNode } from 'react'
import { Alert } from '@/shared/ui/alert'
import { Select } from '@/shared/ui/select'
import { Button } from '@/shared/ui/button'
import { TextField } from '@/shared/ui/text-field'

export function DialogFrame({
  open,
  onOpenChange,
  title,
  description,
  children,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  children: ReactNode
}) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="dialog-overlay bg-foreground/40 fixed inset-0 z-50" />
        <Dialog.Content className="dialog-content border-border bg-background shadow-brutal fixed top-1/2 left-1/2 z-50 flex w-[min(92vw,460px)] -translate-x-1/2 -translate-y-1/2 flex-col gap-4 border-[2.5px] p-4">
          <div className="flex items-start justify-between gap-3">
            <div className="flex flex-col gap-1">
              <Dialog.Title className="text-xl font-black">{title}</Dialog.Title>
              {description ? (
                <Dialog.Description className="text-foreground/70 text-xs">
                  {description}
                </Dialog.Description>
              ) : null}
            </div>
            <Dialog.Close asChild>
              <button
                type="button"
                aria-label="닫기"
                className="border-border bg-card grid size-11 shrink-0 place-items-center border-[2.5px]"
              >
                <X aria-hidden="true" className="size-4" />
              </button>
            </Dialog.Close>
          </div>
          {children}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

export function NodeFormDialog({
  open,
  onOpenChange,
  title,
  description,
  initialName = '',
  initialDescription = '',
  submitLabel,
  pending = false,
  error,
  onSubmit,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  description?: string
  initialName?: string
  initialDescription?: string
  submitLabel: string
  pending?: boolean
  error?: string | null
  onSubmit: (name: string, description: string) => void
}) {
  const [name, setName] = useState(initialName)
  const [details, setDetails] = useState(initialDescription)
  const [validation, setValidation] = useState<string | null>(null)

  function submit(event: FormEvent) {
    event.preventDefault()
    if (!name.trim()) {
      setValidation('이름을 입력해 주세요.')
      return
    }
    setValidation(null)
    onSubmit(name, details)
  }

  return (
    <DialogFrame open={open} onOpenChange={onOpenChange} title={title} description={description}>
      <form className="flex flex-col gap-4" onSubmit={submit}>
        {error ? <Alert variant="error">{error}</Alert> : null}
        {validation ? <Alert variant="error">{validation}</Alert> : null}
        <TextField
          label="이름"
          value={name}
          onChange={(event) => setName(event.target.value)}
          maxLength={120}
          autoFocus
        />
        <label className="flex flex-col gap-2">
          <span className="text-xs font-black">설명 (선택)</span>
          <textarea
            value={details}
            onChange={(event) => setDetails(event.target.value)}
            maxLength={2000}
            rows={3}
            className="border-border bg-card focus-visible:ring-foreground focus-visible:ring-offset-background min-h-20 w-full border-[2.5px] px-3 py-2 text-sm font-medium outline-none focus-visible:ring-2 focus-visible:ring-offset-2"
          />
        </label>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
            취소
          </Button>
          <Button type="submit" disabled={pending} aria-busy={pending}>
            {pending ? '저장 중…' : submitLabel}
          </Button>
        </div>
      </form>
    </DialogFrame>
  )
}

export function MoveDialog({
  open,
  onOpenChange,
  title,
  label,
  options,
  initialId,
  pending = false,
  error,
  onSubmit,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  label: string
  options: Array<{ id: string; name: string }>
  initialId?: string
  pending?: boolean
  error?: string | null
  onSubmit: (parentId: string) => void
}) {
  const [selected, setSelected] = useState(initialId ?? options[0]?.id ?? '')

  return (
    <DialogFrame open={open} onOpenChange={onOpenChange} title={title}>
      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault()
          if (selected && selected !== initialId) onSubmit(selected)
        }}
      >
        {error ? <Alert variant="error">{error}</Alert> : null}
        <label className="flex flex-col gap-2">
          <span className="text-xs font-black">{label}</span>
          <Select value={selected} onChange={(event) => setSelected(event.target.value)}>
            {options.map((option) => (
              <option key={option.id} value={option.id}>
                {option.name}
                {option.id === initialId ? ' (현재)' : ''}
              </option>
            ))}
          </Select>
        </label>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
            취소
          </Button>
          <Button
            type="submit"
            disabled={pending || !selected || selected === initialId}
            aria-busy={pending}
          >
            {pending ? '이동 중…' : '이동'}
          </Button>
        </div>
      </form>
    </DialogFrame>
  )
}

export function DeleteDialog({
  open,
  onOpenChange,
  name,
  pending = false,
  error,
  onConfirm,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  name: string
  pending?: boolean
  error?: string | null
  onConfirm: () => void
}) {
  return (
    <AlertDialog.Root open={open} onOpenChange={onOpenChange}>
      <AlertDialog.Portal>
        <AlertDialog.Overlay className="dialog-overlay bg-foreground/40 fixed inset-0 z-50" />
        <AlertDialog.Content className="dialog-content border-border bg-background shadow-brutal fixed top-1/2 left-1/2 z-50 flex w-[min(92vw,420px)] -translate-x-1/2 -translate-y-1/2 flex-col gap-4 border-[2.5px] p-4">
          <AlertDialog.Title className="text-xl font-black">삭제할까요?</AlertDialog.Title>
          <AlertDialog.Description className="text-foreground/70 text-xs">
            “{name}”을(를) 삭제합니다. 하위 항목이 있으면 삭제할 수 없습니다.
          </AlertDialog.Description>
          {error ? <Alert variant="error">{error}</Alert> : null}
          <div className="flex justify-end gap-2">
            <AlertDialog.Cancel asChild>
              <Button type="button" variant="secondary">
                취소
              </Button>
            </AlertDialog.Cancel>
            <Button type="button" onClick={onConfirm} disabled={pending} aria-busy={pending}>
              {pending ? '삭제 중…' : '삭제'}
            </Button>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  )
}
