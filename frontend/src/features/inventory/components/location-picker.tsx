import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import type { LocationInput } from '../api'
import { hierarchyApi } from '@/features/hierarchy/api'
import { sharingApi } from '@/features/sharing/api'
import { Alert } from '@/shared/ui/alert'
import { Select } from '@/shared/ui/select'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { permissionLabel } from '@/shared/lib/permission'

type Source = 'owned' | 'shared'

function shareKey(type: 'ROOM' | 'SHELF', id: string | null) {
  return id ? `${type}:${id}` : ''
}

export function LocationPicker({
  value,
  onChange,
  disabled = false,
  defaultSource = 'owned',
}: {
  value: LocationInput
  onChange: (value: LocationInput) => void
  disabled?: boolean
  defaultSource?: Source
}) {
  const [source, setSource] = useState<Source>(defaultSource)
  const [sharedKey, setSharedKey] = useState('')
  const [sharedError, setSharedError] = useState<string | null>(null)

  const roomId = value.roomId ?? ''
  const shelfId = value.shelfId ?? ''
  const boxId = value.boxId ?? ''

  const roomsQuery = useQuery({
    queryKey: ['hierarchy', 'rooms', 'picker'],
    queryFn: () => hierarchyApi.listRooms(undefined, 100),
    enabled: source === 'owned',
  })
  const sharesQuery = useQuery({
    queryKey: ['sharing', 'shared'],
    queryFn: sharingApi.shared,
  })
  const sharedOptions = (sharesQuery.data?.items ?? []).filter(
    (share) => share.permission === 'MANAGE',
  )
  const matchedShare = sharedOptions.find((share) =>
    share.resourceType === 'ROOM' ? share.roomId === roomId : share.shelfId === shelfId,
  )
  const selectedSharedKey =
    sharedKey ||
    (matchedShare
      ? matchedShare.resourceType === 'ROOM'
        ? shareKey('ROOM', matchedShare.roomId)
        : shareKey('SHELF', matchedShare.shelfId)
      : '')
  const selectedShare = sharedOptions.find((share) =>
    share.resourceType === 'ROOM'
      ? shareKey('ROOM', share.roomId) === selectedSharedKey
      : shareKey('SHELF', share.shelfId) === selectedSharedKey,
  )
  const sharedRoomId = selectedSharedKey.startsWith('ROOM:') ? selectedSharedKey.slice(5) : roomId
  const sharedShelfId = selectedSharedKey.startsWith('SHELF:') ? selectedSharedKey.slice(6) : ''

  const sharedShelfQuery = useQuery({
    queryKey: ['shared', 'shelf', sharedShelfId],
    queryFn: () => sharingApi.sharedShelf(sharedShelfId),
    enabled: Boolean(sharedShelfId),
  })
  const effectiveSharedRoomId = sharedShelfQuery.data?.roomId ?? sharedRoomId

  const shelvesQuery = useQuery({
    queryKey: ['hierarchy', 'shelves', 'picker', source, effectiveSharedRoomId],
    queryFn: () => hierarchyApi.listShelves(effectiveSharedRoomId, undefined, 100),
    enabled: Boolean(effectiveSharedRoomId) && (source === 'owned' || !sharedShelfId),
  })
  const boxesQuery = useQuery({
    queryKey: ['hierarchy', 'boxes', 'picker', source, shelfId || sharedShelfId],
    queryFn: () => hierarchyApi.listBoxes(shelfId || sharedShelfId, undefined, 100),
    enabled: Boolean(shelfId || sharedShelfId),
  })

  const rooms = roomsQuery.data?.items ?? []
  const shelves = shelvesQuery.data?.items ?? []
  const boxes = boxesQuery.data?.items ?? []
  const room = rooms.find((item) => item.id === roomId)
  const shelf = shelves.find((item) => item.id === shelfId)
  const box = boxes.find((item) => item.id === boxId)
  const ownedPermission =
    box?.effectivePermission ?? shelf?.effectivePermission ?? room?.effectivePermission
  const permission = source === 'shared' ? selectedShare?.permission : ownedPermission

  async function selectShared(key: string) {
    setSharedKey(key)
    setSharedError(null)
    if (!key) {
      onChange({})
      return
    }
    if (key.startsWith('ROOM:')) {
      onChange({ roomId: key.slice(5) })
      return
    }
    const shelfKey = key.slice(6)
    try {
      const sharedShelf = await sharingApi.sharedShelf(shelfKey)
      onChange({ roomId: sharedShelf.roomId, shelfId: shelfKey })
    } catch {
      setSharedError('공유 선반 정보를 불러오지 못했습니다.')
      onChange({})
    }
  }

  function switchSource(next: Source) {
    if (next === source) return
    setSource(next)
    setSharedKey('')
    setSharedError(null)
    onChange({})
  }

  if (source === 'owned' && roomsQuery.isError) {
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

  if (source === 'owned' && (roomsQuery.isLoading || sharesQuery.isLoading)) {
    return (
      <p role="status" className="text-xs font-bold">
        공간을 불러오는 중…
      </p>
    )
  }

  const noOwnedRooms = source === 'owned' && rooms.length === 0

  return (
    <fieldset className="flex flex-col gap-3" disabled={disabled}>
      <legend className="text-xs font-black">위치</legend>

      {sharedOptions.length > 0 || defaultSource === 'shared' ? (
        <div className="border-border grid grid-cols-2 border-[2.5px]">
          <button
            type="button"
            aria-pressed={source === 'owned'}
            onClick={() => switchSource('owned')}
            className={
              source === 'owned'
                ? 'bg-primary text-on-accent h-11 text-xs font-black'
                : 'bg-card h-11 text-xs font-black'
            }
          >
            내 공간
          </button>
          <button
            type="button"
            aria-pressed={source === 'shared'}
            onClick={() => switchSource('shared')}
            className={
              source === 'shared'
                ? 'bg-primary text-on-accent h-11 text-xs font-black'
                : 'bg-card h-11 text-xs font-black'
            }
          >
            공유받은 공간
          </button>
        </div>
      ) : null}

      {sharedError ? <Alert variant="error">{sharedError}</Alert> : null}

      {source === 'owned' ? (
        noOwnedRooms ? (
          <Alert variant="info">
            먼저 공간을 만들어 주세요.{' '}
            <Link to="/space" className="tap-area font-black underline underline-offset-2">
              공간 지도로 이동
            </Link>
          </Alert>
        ) : (
          <>
            <div className="grid gap-2 sm:grid-cols-3">
              <label className="flex flex-col gap-2">
                <span className="text-label font-black">방</span>
                <Select
                  aria-label="방"
                  value={roomId}
                  onChange={(event) => {
                    const next = event.target.value
                    onChange(next ? { roomId: next } : {})
                  }}
                >
                  <option value="">선택</option>
                  {rooms.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </Select>
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-label font-black">선반 (선택)</span>
                <Select
                  aria-label="선반"
                  value={shelfId}
                  disabled={!roomId}
                  onChange={(event) => {
                    const next = event.target.value
                    onChange(next ? { roomId, shelfId: next } : { roomId })
                  }}
                >
                  <option value="">선택 안 함</option>
                  {shelves.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </Select>
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-label font-black">박스 (선택)</span>
                <Select
                  aria-label="박스"
                  value={boxId}
                  disabled={!shelfId}
                  onChange={(event) => {
                    const next = event.target.value
                    onChange(next ? { roomId, shelfId, boxId: next } : { roomId, shelfId })
                  }}
                >
                  <option value="">선택 안 함</option>
                  {boxes.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </Select>
              </label>
            </div>
            <div className="flex flex-wrap items-center gap-2 text-xs">
              {room ? <span className="font-bold">{room.name}</span> : null}
              {shelf ? <span>› {shelf.name}</span> : null}
              {box ? <span>› {box.name}</span> : null}
              {permission ? <Badge variant="neutral">{permissionLabel(permission)}</Badge> : null}
              {!room ? <span className="text-foreground/60">위치를 선택해 주세요.</span> : null}
            </div>
          </>
        )
      ) : (
        <>
          <label className="flex flex-col gap-2">
            <span className="text-label font-black">공유받은 위치</span>
            <Select
              aria-label="공유받은 위치"
              value={selectedSharedKey}
              onChange={(event) => void selectShared(event.target.value)}
            >
              <option value="">선택</option>
              {sharedOptions.map((share) => {
                const key =
                  share.resourceType === 'ROOM'
                    ? shareKey('ROOM', share.roomId)
                    : shareKey('SHELF', share.shelfId)
                return (
                  <option key={key} value={key}>
                    {share.resourceType === 'ROOM' ? '방' : '선반'} · {share.resourceName}
                  </option>
                )
              })}
            </Select>
          </label>

          {sharedKey.startsWith('ROOM:') ? (
            <div className="grid gap-2 sm:grid-cols-2">
              <label className="flex flex-col gap-2">
                <span className="text-label font-black">선반 (선택)</span>
                <Select
                  aria-label="공유 선반"
                  value={shelfId}
                  onChange={(event) => {
                    const next = event.target.value
                    onChange(
                      next
                        ? { roomId: effectiveSharedRoomId, shelfId: next }
                        : { roomId: effectiveSharedRoomId },
                    )
                  }}
                >
                  <option value="">선택 안 함</option>
                  {shelves.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </Select>
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-label font-black">박스 (선택)</span>
                <Select
                  aria-label="공유 박스"
                  value={boxId}
                  disabled={!shelfId}
                  onChange={(event) => {
                    const next = event.target.value
                    onChange(
                      next
                        ? { roomId: effectiveSharedRoomId, shelfId, boxId: next }
                        : { roomId: effectiveSharedRoomId, shelfId },
                    )
                  }}
                >
                  <option value="">선택 안 함</option>
                  {boxes.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </Select>
              </label>
            </div>
          ) : null}

          {sharedKey.startsWith('SHELF:') ? (
            <label className="flex flex-col gap-2">
              <span className="text-label font-black">박스 (선택)</span>
              <Select
                aria-label="공유 박스"
                value={boxId}
                onChange={(event) => {
                  const next = event.target.value
                  onChange(
                    next
                      ? { roomId: effectiveSharedRoomId, shelfId: sharedShelfId, boxId: next }
                      : { roomId: effectiveSharedRoomId, shelfId: sharedShelfId },
                  )
                }}
              >
                <option value="">선택 안 함</option>
                {boxes.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </Select>
            </label>
          ) : null}

          <div className="flex flex-wrap items-center gap-2 text-xs">
            {selectedShare ? (
              <>
                <span className="font-bold">
                  {selectedShare.resourceType === 'ROOM' ? '방' : '선반'} ·{' '}
                  {selectedShare.resourceName}
                </span>
                <Badge variant="neutral">{permissionLabel(selectedShare.permission)}</Badge>
              </>
            ) : (
              <span className="text-foreground/60">공유받은 위치를 선택해 주세요.</span>
            )}
          </div>
        </>
      )}
    </fieldset>
  )
}
