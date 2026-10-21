import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import type { LocationInput } from '../api'
import { hierarchyApi } from '@/features/hierarchy/api'
import { Alert } from '@/shared/ui/alert'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { permissionLabel } from '@/shared/lib/permission'

export function LocationPicker({
  value,
  onChange,
  disabled = false,
}: {
  value: LocationInput
  onChange: (value: LocationInput) => void
  disabled?: boolean
}) {
  const roomId = value.roomId ?? ''
  const shelfId = value.shelfId ?? ''
  const boxId = value.boxId ?? ''

  const roomsQuery = useQuery({
    queryKey: ['hierarchy', 'rooms', 'picker'],
    queryFn: () => hierarchyApi.listRooms(undefined, 100),
  })
  const shelvesQuery = useQuery({
    queryKey: ['hierarchy', 'shelves', 'picker', roomId],
    queryFn: () => hierarchyApi.listShelves(roomId, undefined, 100),
    enabled: Boolean(roomId),
  })
  const boxesQuery = useQuery({
    queryKey: ['hierarchy', 'boxes', 'picker', shelfId],
    queryFn: () => hierarchyApi.listBoxes(shelfId, undefined, 100),
    enabled: Boolean(shelfId),
  })

  const rooms = roomsQuery.data?.items ?? []
  const shelves = shelvesQuery.data?.items ?? []
  const boxes = boxesQuery.data?.items ?? []
  const room = rooms.find((item) => item.id === roomId)
  const shelf = shelves.find((item) => item.id === shelfId)
  const box = boxes.find((item) => item.id === boxId)
  const permission =
    box?.effectivePermission ?? shelf?.effectivePermission ?? room?.effectivePermission

  if (roomsQuery.isError) {
    return (
      <div className="flex flex-col gap-2">
        <Alert variant="error">공간을 불러오지 못했습니다.</Alert>
        <Button
          type="button"
          size="sm"
          variant="secondary"
          onClick={() => void roomsQuery.refetch()}
        >
          다시 시도
        </Button>
      </div>
    )
  }

  if (roomsQuery.isLoading) {
    return (
      <p role="status" className="text-xs font-bold">
        공간을 불러오는 중…
      </p>
    )
  }

  if (rooms.length === 0) {
    return (
      <Alert variant="info">
        먼저 공간을 만들어 주세요.{' '}
        <Link to="/space" className="font-black underline underline-offset-2">
          공간 지도로 이동
        </Link>
      </Alert>
    )
  }

  return (
    <fieldset className="flex flex-col gap-3" disabled={disabled}>
      <legend className="text-xs font-black">위치</legend>
      <div className="grid gap-2 sm:grid-cols-3">
        <label className="flex flex-col gap-1.5">
          <span className="text-[11px] font-black">방</span>
          <select
            aria-label="방"
            value={roomId}
            onChange={(event) => {
              const next = event.target.value
              onChange(next ? { roomId: next } : {})
            }}
            className="border-border bg-card h-12 border-[2.5px] px-3 text-sm"
          >
            <option value="">선택</option>
            {rooms.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1.5">
          <span className="text-[11px] font-black">선반 (선택)</span>
          <select
            aria-label="선반"
            value={shelfId}
            disabled={!roomId}
            onChange={(event) => {
              const next = event.target.value
              onChange(next ? { roomId, shelfId: next } : { roomId })
            }}
            className="border-border bg-card h-12 border-[2.5px] px-3 text-sm disabled:opacity-50"
          >
            <option value="">선택 안 함</option>
            {shelves.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1.5">
          <span className="text-[11px] font-black">박스 (선택)</span>
          <select
            aria-label="박스"
            value={boxId}
            disabled={!shelfId}
            onChange={(event) => {
              const next = event.target.value
              onChange(next ? { roomId, shelfId, boxId: next } : { roomId, shelfId })
            }}
            className="border-border bg-card h-12 border-[2.5px] px-3 text-sm disabled:opacity-50"
          >
            <option value="">선택 안 함</option>
            {boxes.map((item) => (
              <option key={item.id} value={item.id}>
                {item.name}
              </option>
            ))}
          </select>
        </label>
      </div>
      <div className="flex flex-wrap items-center gap-2 text-[11px]">
        {room ? <span className="font-bold">{room.name}</span> : null}
        {shelf ? <span>› {shelf.name}</span> : null}
        {box ? <span>› {box.name}</span> : null}
        {permission ? <Badge variant="neutral">{permissionLabel(permission)}</Badge> : null}
        {!room ? <span className="text-foreground/60">위치를 선택해 주세요.</span> : null}
      </div>
    </fieldset>
  )
}
