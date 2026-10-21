import * as Dialog from '@radix-ui/react-dialog'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { X } from 'lucide-react'
import { useState } from 'react'
import { inventoryApi, type Inventory, type LocationInput } from '../api'
import { LocationPicker } from './location-picker'
import { ApiError } from '@/shared/api/client'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'

export function MoveInventoryDialog({
  inventory,
  onClose,
  onMoved,
}: {
  inventory: Inventory
  onClose: () => void
  onMoved?: () => void
}) {
  const queryClient = useQueryClient()
  const [location, setLocation] = useState<LocationInput>({
    roomId: inventory.location.roomId ?? undefined,
    shelfId: inventory.location.shelfId ?? undefined,
    boxId: inventory.location.boxId ?? undefined,
  })
  const [error, setError] = useState<string | null>(null)

  const mutation = useMutation({
    mutationFn: () =>
      inventoryApi.moveInventory(inventory.id, {
        version: inventory.version,
        roomId: location.roomId ?? null,
        shelfId: location.shelfId ?? null,
        boxId: location.boxId ?? null,
      }),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['inventory'] }),
        queryClient.invalidateQueries({ queryKey: ['hierarchy'] }),
      ])
      onClose()
      onMoved?.()
    },
    onError: (cause) => {
      if (cause instanceof ApiError && cause.status === 409) {
        setError('다른 곳에서 먼저 수정했어요. 최신 정보를 다시 불러온 뒤 이동해 주세요.')
        return
      }
      setError(cause instanceof ApiError ? cause.message : '이동하지 못했습니다.')
    },
  })

  const sameLocation =
    (inventory.location.roomId ?? '') === (location.roomId ?? '') &&
    (inventory.location.shelfId ?? '') === (location.shelfId ?? '') &&
    (inventory.location.boxId ?? '') === (location.boxId ?? '')

  return (
    <Dialog.Root open onOpenChange={(next) => !next && onClose()}>
      <Dialog.Portal>
        <Dialog.Overlay className="bg-foreground/40 fixed inset-0 z-50" />
        <Dialog.Content className="border-border bg-background shadow-brutal fixed top-1/2 left-1/2 z-50 w-[min(92vw,480px)] -translate-x-1/2 -translate-y-1/2 border-[2.5px] p-5">
          <div className="flex items-start justify-between gap-3">
            <div className="flex flex-col gap-1">
              <Dialog.Title className="text-xl font-black">위치 이동</Dialog.Title>
              <Dialog.Description className="text-foreground/60 text-xs">
                {inventory.displayName}을(를) 어디로 옮길까요?
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

          <div className="mt-4 flex flex-col gap-4">
            {error ? <Alert variant="error">{error}</Alert> : null}
            <LocationPicker
              value={location}
              onChange={(next) => {
                setLocation(next)
                setError(null)
              }}
            />
            <div className="flex justify-end gap-2">
              <Button type="button" variant="secondary" onClick={onClose}>
                취소
              </Button>
              <Button
                type="button"
                disabled={mutation.isPending || sameLocation || !location.roomId}
                onClick={() => {
                  if (sameLocation) {
                    onClose()
                    return
                  }
                  mutation.mutate()
                }}
              >
                {mutation.isPending ? '이동 중…' : sameLocation ? '현재 위치' : '이동'}
              </Button>
            </div>
          </div>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}
