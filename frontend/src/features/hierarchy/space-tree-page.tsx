import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Box as BoxIcon,
  ChevronDown,
  ChevronRight,
  Layers,
  Pencil,
  Plus,
  Square,
  Trash2,
  MoveRight,
} from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { hierarchyApi, type Box, type Permission, type Room, type Shelf } from './api'
import { DeleteDialog, MoveDialog, NodeFormDialog } from './components/hierarchy-dialogs'
import { ApiError } from '@/shared/api/client'
import { Alert } from '@/shared/ui/alert'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { MetaText, PageTitle, SectionTitle } from '@/shared/ui/typography'

type NodeKind = 'room' | 'shelf' | 'box'
type AnyNode = Room | Shelf | Box

function permissionLabel(permission: Permission) {
  return permission === 'OWNER' ? '소유자' : permission
}

function NodeActions({
  onAdd,
  onEdit,
  onDelete,
  onMove,
}: {
  onAdd?: () => void
  onEdit: () => void
  onDelete: () => void
  onMove?: () => void
}) {
  return (
    <div className="flex items-center gap-1">
      {onAdd ? (
        <Button type="button" size="icon" variant="ghost" onClick={onAdd} aria-label="하위 추가">
          <Plus aria-hidden="true" className="size-4" />
        </Button>
      ) : null}
      {onMove ? (
        <Button type="button" size="icon" variant="ghost" onClick={onMove} aria-label="이동">
          <MoveRight aria-hidden="true" className="size-4" />
        </Button>
      ) : null}
      <Button type="button" size="icon" variant="ghost" onClick={onEdit} aria-label="수정">
        <Pencil aria-hidden="true" className="size-4" />
      </Button>
      <Button type="button" size="icon" variant="ghost" onClick={onDelete} aria-label="삭제">
        <Trash2 aria-hidden="true" className="size-4" />
      </Button>
    </div>
  )
}

export function SpaceTreePage() {
  const queryClient = useQueryClient()
  const [expandedRooms, setExpandedRooms] = useState<Set<string>>(new Set())
  const [createTarget, setCreateTarget] = useState<{ kind: NodeKind; parentId?: string } | null>(
    null,
  )
  const [editTarget, setEditTarget] = useState<{ kind: NodeKind; node: AnyNode } | null>(null)
  const [moveTarget, setMoveTarget] = useState<Shelf | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<{ kind: NodeKind; node: AnyNode } | null>(null)
  const [mutationError, setMutationError] = useState<string | null>(null)

  const roomsQuery = useInfiniteQuery({
    queryKey: ['hierarchy', 'rooms', 'tree'],
    queryFn: ({ pageParam }) => hierarchyApi.listRooms(pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['hierarchy'] })
  }

  const saveMutation = useMutation({
    mutationFn: async (input: {
      mode: 'create' | 'edit'
      kind: NodeKind
      parentId?: string
      node?: AnyNode
      name: string
      description: string
    }) => {
      if (input.mode === 'create') {
        if (input.kind === 'room')
          return hierarchyApi.createRoom({ name: input.name, description: input.description })
        if (input.kind === 'shelf') {
          if (!input.parentId) throw new Error('상위 Room이 없습니다.')
          return hierarchyApi.createShelf(input.parentId, {
            name: input.name,
            description: input.description,
          })
        }
        if (!input.parentId) throw new Error('상위 Shelf가 없습니다.')
        return hierarchyApi.createBox(input.parentId, {
          name: input.name,
          description: input.description,
        })
      }
      if (!input.node) throw new Error('수정할 항목이 없습니다.')
      const update = {
        name: input.name,
        description: input.description,
        version: input.node.version,
      }
      if (input.kind === 'room') return hierarchyApi.updateRoom(input.node.id, update)
      if (input.kind === 'shelf') return hierarchyApi.updateShelf(input.node.id, update)
      return hierarchyApi.updateBox(input.node.id, update)
    },
    onSuccess: async () => {
      setCreateTarget(null)
      setEditTarget(null)
      setMutationError(null)
      await invalidate()
    },
    onError: (error) =>
      setMutationError(error instanceof ApiError ? error.message : '저장하지 못했습니다.'),
  })

  const deleteMutation = useMutation({
    mutationFn: async (target: { kind: NodeKind; node: AnyNode }) => {
      if (target.kind === 'room') return hierarchyApi.deleteRoom(target.node.id)
      if (target.kind === 'shelf') return hierarchyApi.deleteShelf(target.node.id)
      return hierarchyApi.deleteBox(target.node.id)
    },
    onSuccess: async () => {
      setDeleteTarget(null)
      setMutationError(null)
      await invalidate()
    },
    onError: (error) =>
      setMutationError(error instanceof ApiError ? error.message : '삭제하지 못했습니다.'),
  })

  const moveMutation = useMutation({
    mutationFn: async ({ shelf, roomId }: { shelf: Shelf; roomId: string }) =>
      hierarchyApi.moveShelf(shelf.id, roomId, shelf.version),
    onSuccess: async () => {
      setMoveTarget(null)
      setMutationError(null)
      await invalidate()
    },
    onError: (error) =>
      setMutationError(error instanceof ApiError ? error.message : '이동하지 못했습니다.'),
  })

  const rooms = roomsQuery.data?.pages.flatMap((page) => page.items) ?? []
  const roomsForMove = rooms.map((room) => ({ id: room.id, name: room.name }))

  function toggleRoom(roomId: string) {
    setExpandedRooms((current) => {
      const next = new Set(current)
      if (next.has(roomId)) next.delete(roomId)
      else next.add(roomId)
      return next
    })
  }

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-col gap-1">
          <p className="label-caps text-foreground/60">SCR-004 · 공간</p>
          <PageTitle>공간 지도</PageTitle>
          <MetaText>
            Room &gt; Shelf &gt; Box 순서로 물건을 정리합니다. 소유자만 계층을 변경할 수 있어요.
          </MetaText>
        </div>
        <Button type="button" onClick={() => setCreateTarget({ kind: 'room' })}>
          <Plus aria-hidden="true" className="size-4" />
          Room 추가
        </Button>
      </div>

      {mutationError && !createTarget && !editTarget && !deleteTarget && !moveTarget ? (
        <Alert variant="error">{mutationError}</Alert>
      ) : null}

      {roomsQuery.isLoading ? (
        <Card>
          <CardBody>
            <p className="text-sm font-bold" role="status">
              공간을 불러오는 중…
            </p>
          </CardBody>
        </Card>
      ) : roomsQuery.isError ? (
        <Card>
          <CardBody className="items-start gap-3">
            <Alert variant="error">공간을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.</Alert>
            <Button type="button" variant="secondary" onClick={() => void roomsQuery.refetch()}>
              다시 시도
            </Button>
          </CardBody>
        </Card>
      ) : rooms.length === 0 ? (
        <Card>
          <CardBody className="items-start gap-3">
            <SectionTitle>아직 방이 없어요</SectionTitle>
            <p className="text-foreground/70 text-xs">
              첫 Room을 만들고 Shelf와 Box를 추가해 보세요.
            </p>
            <Button type="button" onClick={() => setCreateTarget({ kind: 'room' })}>
              방 만들기
            </Button>
          </CardBody>
        </Card>
      ) : (
        <div className="flex flex-col gap-2" role="tree" aria-label="공간 계층">
          {rooms.map((room) => (
            <RoomBranch
              key={room.id}
              room={room}
              expanded={expandedRooms.has(room.id)}
              onToggle={() => toggleRoom(room.id)}
              onCreateShelf={() => setCreateTarget({ kind: 'shelf', parentId: room.id })}
              onEdit={() => setEditTarget({ kind: 'room', node: room })}
              onDelete={() => setDeleteTarget({ kind: 'room', node: room })}
              onAddBox={(shelf) => setCreateTarget({ kind: 'box', parentId: shelf.id })}
              onEditShelf={(shelf) => setEditTarget({ kind: 'shelf', node: shelf })}
              onDeleteShelf={(shelf) => setDeleteTarget({ kind: 'shelf', node: shelf })}
              onMoveShelf={(shelf) => setMoveTarget(shelf)}
              onEditBox={(box) => setEditTarget({ kind: 'box', node: box })}
              onDeleteBox={(box) => setDeleteTarget({ kind: 'box', node: box })}
            />
          ))}
          {roomsQuery.hasNextPage ? (
            <Button
              type="button"
              variant="secondary"
              disabled={roomsQuery.isFetchingNextPage}
              onClick={() => void roomsQuery.fetchNextPage()}
            >
              {roomsQuery.isFetchingNextPage ? '불러오는 중…' : 'Room 더 보기'}
            </Button>
          ) : null}
        </div>
      )}

      <NodeFormDialog
        key={`create-${createTarget?.kind ?? ''}-${createTarget?.parentId ?? ''}`}
        open={createTarget !== null}
        onOpenChange={(open) => !open && setCreateTarget(null)}
        title={
          createTarget?.kind === 'room'
            ? 'Room 추가'
            : createTarget?.kind === 'shelf'
              ? 'Shelf 추가'
              : 'Box 추가'
        }
        submitLabel="추가"
        pending={saveMutation.isPending}
        error={mutationError}
        onSubmit={(name, description) =>
          createTarget &&
          saveMutation.mutate({
            mode: 'create',
            kind: createTarget.kind,
            parentId: createTarget.parentId,
            name,
            description,
          })
        }
      />
      <NodeFormDialog
        key={`edit-${editTarget?.kind ?? ''}-${editTarget?.node.id ?? ''}`}
        open={editTarget !== null}
        onOpenChange={(open) => !open && setEditTarget(null)}
        title="이름과 설명 수정"
        initialName={editTarget?.node.name ?? ''}
        initialDescription={editTarget?.node.description ?? ''}
        submitLabel="저장"
        pending={saveMutation.isPending}
        error={mutationError}
        onSubmit={(name, description) =>
          editTarget &&
          saveMutation.mutate({
            mode: 'edit',
            kind: editTarget.kind,
            node: editTarget.node,
            name,
            description,
          })
        }
      />
      <MoveDialog
        key={`move-${moveTarget?.id ?? ''}`}
        open={moveTarget !== null}
        onOpenChange={(open) => !open && setMoveTarget(null)}
        title="Shelf 이동"
        label="이동할 Room"
        options={roomsForMove}
        initialId={moveTarget?.roomId}
        pending={moveMutation.isPending}
        error={mutationError}
        onSubmit={(roomId) => moveTarget && moveMutation.mutate({ shelf: moveTarget, roomId })}
      />
      <DeleteDialog
        open={deleteTarget !== null}
        onOpenChange={(open) => !open && setDeleteTarget(null)}
        name={deleteTarget?.node.name ?? ''}
        pending={deleteMutation.isPending}
        error={mutationError}
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget)}
      />
    </div>
  )
}

function RoomBranch({
  room,
  expanded,
  onToggle,
  onCreateShelf,
  onEdit,
  onDelete,
  onAddBox,
  onEditShelf,
  onDeleteShelf,
  onMoveShelf,
  onEditBox,
  onDeleteBox,
}: {
  room: Room
  expanded: boolean
  onToggle: () => void
  onCreateShelf: () => void
  onEdit: () => void
  onDelete: () => void
  onAddBox: (shelf: Shelf) => void
  onEditShelf: (shelf: Shelf) => void
  onDeleteShelf: (shelf: Shelf) => void
  onMoveShelf: (shelf: Shelf) => void
  onEditBox: (box: Box) => void
  onDeleteBox: (box: Box) => void
}) {
  const shelvesQuery = useInfiniteQuery({
    queryKey: ['hierarchy', 'shelves', room.id, 'tree'],
    queryFn: ({ pageParam }) => hierarchyApi.listShelves(room.id, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
    enabled: expanded,
  })
  const shelves = shelvesQuery.data?.pages.flatMap((page) => page.items) ?? []

  return (
    <div className="flex flex-col gap-2" role="treeitem" aria-expanded={expanded} aria-level={1}>
      <div className="border-border bg-card flex flex-wrap items-center gap-2 border-[2.5px] px-3 py-2.5">
        <button
          type="button"
          onClick={onToggle}
          aria-label={expanded ? 'Room 접기' : 'Room 펼치기'}
          className="border-border grid size-8 place-items-center border-2"
        >
          {expanded ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
        </button>
        <Square aria-hidden="true" className="size-4" />
        <Link to={`/space/rooms/${room.id}`} className="min-w-0 text-sm font-black hover:underline">
          {room.name}
        </Link>
        <Badge variant="neutral">Shelf {room.shelfCount}</Badge>
        <Badge variant="neutral">{permissionLabel(room.effectivePermission)}</Badge>
        {room.isShared ? <Badge variant="info">공유</Badge> : null}
        {room.effectivePermission === 'OWNER' ? (
          <div className="ml-auto flex items-center gap-1">
            <NodeActions onAdd={onCreateShelf} onEdit={onEdit} onDelete={onDelete} />
          </div>
        ) : null}
      </div>
      {expanded ? (
        <div className="border-border ml-[22px] flex flex-col gap-2 border-l-[2.5px] pl-[14px]">
          {shelvesQuery.isLoading ? (
            <p className="text-xs" role="status">
              Shelf를 불러오는 중…
            </p>
          ) : null}
          {shelvesQuery.isError ? (
            <div className="flex flex-col items-start gap-2">
              <Alert variant="error">Shelf를 불러오지 못했습니다.</Alert>
              <Button
                type="button"
                size="sm"
                variant="secondary"
                onClick={() => void shelvesQuery.refetch()}
              >
                다시 시도
              </Button>
            </div>
          ) : shelves.length === 0 && !shelvesQuery.isLoading ? (
            <p className="text-foreground/60 text-xs">Shelf가 없습니다. + 버튼으로 추가하세요.</p>
          ) : null}
          {shelves.map((shelf) => (
            <ShelfBranch
              key={shelf.id}
              shelf={shelf}
              onAddBox={() => onAddBox(shelf)}
              onEdit={() => onEditShelf(shelf)}
              onDelete={() => onDeleteShelf(shelf)}
              onMove={() => onMoveShelf(shelf)}
              onEditBox={onEditBox}
              onDeleteBox={onDeleteBox}
            />
          ))}
          {shelvesQuery.hasNextPage ? (
            <Button
              type="button"
              size="sm"
              variant="secondary"
              disabled={shelvesQuery.isFetchingNextPage}
              onClick={() => void shelvesQuery.fetchNextPage()}
            >
              {shelvesQuery.isFetchingNextPage ? '불러오는 중…' : 'Shelf 더 보기'}
            </Button>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}

function ShelfBranch({
  shelf,
  onAddBox,
  onEdit,
  onDelete,
  onMove,
  onEditBox,
  onDeleteBox,
}: {
  shelf: Shelf
  onAddBox: () => void
  onEdit: () => void
  onDelete: () => void
  onMove: () => void
  onEditBox: (box: Box) => void
  onDeleteBox: (box: Box) => void
}) {
  const [expanded, setExpanded] = useState(false)
  const boxesQuery = useInfiniteQuery({
    queryKey: ['hierarchy', 'boxes', shelf.id, 'tree'],
    queryFn: ({ pageParam }) => hierarchyApi.listBoxes(shelf.id, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
    enabled: expanded,
  })
  const boxes = boxesQuery.data?.pages.flatMap((page) => page.items) ?? []

  return (
    <div className="flex flex-col gap-2" role="treeitem" aria-expanded={expanded} aria-level={2}>
      <div className="border-border bg-yellow-soft flex flex-wrap items-center gap-2 border-[2.5px] px-3 py-2.5">
        <button
          type="button"
          onClick={() => setExpanded((value) => !value)}
          aria-label={expanded ? 'Shelf 접기' : 'Shelf 펼치기'}
          className="border-border grid size-8 place-items-center border-2"
        >
          {expanded ? <ChevronDown className="size-4" /> : <ChevronRight className="size-4" />}
        </button>
        <Layers aria-hidden="true" className="size-4" />
        <Link
          to={`/space/shelves/${shelf.id}`}
          className="min-w-0 text-sm font-black hover:underline"
        >
          {shelf.name}
        </Link>
        <Badge variant="neutral">Box {shelf.boxCount}</Badge>
        <span className="text-[10px] font-bold">{permissionLabel(shelf.effectivePermission)}</span>
        {shelf.effectivePermission === 'OWNER' ? (
          <div className="ml-auto flex items-center gap-1">
            <NodeActions onAdd={onAddBox} onEdit={onEdit} onDelete={onDelete} onMove={onMove} />
          </div>
        ) : null}
      </div>
      {expanded ? (
        <div className="border-border ml-[22px] flex flex-col gap-2 border-l-[2.5px] pl-[14px]">
          {boxesQuery.isLoading ? (
            <p className="text-xs" role="status">
              Box를 불러오는 중…
            </p>
          ) : null}
          {boxesQuery.isError ? (
            <div className="flex flex-col items-start gap-2">
              <Alert variant="error">Box를 불러오지 못했습니다.</Alert>
              <Button
                type="button"
                size="sm"
                variant="secondary"
                onClick={() => void boxesQuery.refetch()}
              >
                다시 시도
              </Button>
            </div>
          ) : boxes.length === 0 && !boxesQuery.isLoading ? (
            <p className="text-foreground/60 text-xs">Box가 없습니다.</p>
          ) : null}
          {boxes.map((box) => (
            <div
              key={box.id}
              className="border-border bg-blue-soft flex items-center gap-2 border-[2.5px] px-3 py-2"
            >
              <BoxIcon aria-hidden="true" className="size-4" />
              <Link
                to={`/space/boxes/${box.id}`}
                className="min-w-0 text-sm font-black hover:underline"
              >
                {box.name}
              </Link>
              {box.effectivePermission === 'OWNER' ? (
                <div className="ml-auto">
                  <NodeActions onEdit={() => onEditBox(box)} onDelete={() => onDeleteBox(box)} />
                </div>
              ) : null}
            </div>
          ))}
          {boxesQuery.hasNextPage ? (
            <Button
              type="button"
              size="sm"
              variant="secondary"
              disabled={boxesQuery.isFetchingNextPage}
              onClick={() => void boxesQuery.fetchNextPage()}
            >
              {boxesQuery.isFetchingNextPage ? '불러오는 중…' : 'Box 더 보기'}
            </Button>
          ) : null}
        </div>
      ) : null}
    </div>
  )
}
