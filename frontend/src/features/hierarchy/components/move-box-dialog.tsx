import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { hierarchyApi, type Box } from '../api'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { DialogFrame } from './hierarchy-dialogs'

export function MoveBoxDialog({
  open,
  onOpenChange,
  box,
  currentRoomId,
  currentShelfId,
  rooms,
  pending,
  error,
  onSubmit,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  box: Box | null
  currentRoomId: string
  currentShelfId: string
  rooms: Array<{ id: string; name: string }>
  pending?: boolean
  error?: string | null
  onSubmit: (shelfId: string) => void
}) {
  const [roomId, setRoomId] = useState(currentRoomId)
  const [shelfId, setShelfId] = useState(currentShelfId)
  const shelvesQuery = useQuery({
    queryKey: ['hierarchy', 'shelves', 'move-box', roomId],
    queryFn: () => hierarchyApi.listShelves(roomId, undefined, 100),
    enabled: open && Boolean(roomId),
  })
  const shelves = shelvesQuery.data?.items ?? []

  return (
    <DialogFrame open={open} onOpenChange={onOpenChange} title="Box 이동">
      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault()
          if (shelfId && shelfId !== currentShelfId) onSubmit(shelfId)
        }}
      >
        {error ? <Alert variant="error">{error}</Alert> : null}
        <label className="flex flex-col gap-1.5">
          <span className="text-xs font-black">이동할 Room</span>
          <select
            aria-label="이동할 Room"
            value={roomId}
            onChange={(event) => {
              setRoomId(event.target.value)
              setShelfId('')
            }}
            className="border-border bg-card h-12 border-[2.5px] px-3 text-sm font-medium"
          >
            {rooms.map((room) => (
              <option key={room.id} value={room.id}>
                {room.name}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1.5">
          <span className="text-xs font-black">이동할 Shelf</span>
          <select
            aria-label="이동할 Shelf"
            value={shelfId}
            disabled={shelvesQuery.isLoading}
            onChange={(event) => setShelfId(event.target.value)}
            className="border-border bg-card h-12 border-[2.5px] px-3 text-sm font-medium disabled:opacity-50"
          >
            <option value="">선택</option>
            {shelves.map((shelf) => (
              <option key={shelf.id} value={shelf.id}>
                {shelf.name}
                {shelf.id === currentShelfId ? ' (현재)' : ''}
              </option>
            ))}
          </select>
        </label>
        {box ? <p className="text-foreground/60 text-xs">“{box.name}”을(를) 이동합니다.</p> : null}
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={() => onOpenChange(false)}>
            취소
          </Button>
          <Button
            type="submit"
            disabled={pending || !shelfId || shelfId === currentShelfId}
            aria-busy={pending}
          >
            {pending ? '이동 중…' : '이동'}
          </Button>
        </div>
      </form>
    </DialogFrame>
  )
}
