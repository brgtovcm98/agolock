import * as Dialog from '@radix-ui/react-dialog'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronDown, Plus, X } from 'lucide-react'
import { useRef, useState } from 'react'
import { Link } from 'react-router-dom'
import {
  inventoryApi,
  type Inventory,
  type InventoryStatus,
  type Item,
  type LocationInput,
} from './api'
import type { AddInventoryTarget } from './add-inventory-context'
import { CategoryCombobox } from './components/category-combobox'
import { InventoryImages } from '@/features/media/inventory-images'
import { ItemCombobox } from './components/item-combobox'
import { LocationPicker } from './components/location-picker'
import { useAuth } from '@/features/auth/auth-provider'
import { ApiError } from '@/shared/api/client'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { ConfirmDialog } from '@/shared/ui/confirm-dialog'
import { TextField } from '@/shared/ui/text-field'
import { locationTypeLabel } from '@/shared/lib/format'
import { canEdit } from '@/shared/lib/permission'

type Mode = 'new' | 'existing'

type SuccessState = {
  inventory: Inventory
  itemId: string
  itemName: string
}

class FormError extends Error {}

function newKey() {
  if (typeof crypto !== 'undefined' && 'randomUUID' in crypto) return crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

export function AddInventorySheet({
  onClose,
  context,
}: {
  onClose: () => void
  context: AddInventoryTarget
}) {
  const { user, space } = useAuth()
  const queryClient = useQueryClient()
  const owner = Boolean(user && space && user.id === space.ownerUserId)

  const canCreateNew = owner && context.source !== 'shared'
  const [mode, setMode] = useState<Mode>(
    context.itemId || context.source === 'shared' ? 'existing' : owner ? 'new' : 'existing',
  )
  const [name, setName] = useState('')
  const [price, setPrice] = useState('')
  const [description, setDescription] = useState('')
  const [categoryId, setCategoryId] = useState('')
  const [status, setStatus] = useState<InventoryStatus>('STORED')
  const [location, setLocation] = useState<LocationInput>({
    roomId: context.roomId,
    shelfId: context.shelfId,
    boxId: context.boxId,
  })
  const [selection, setSelection] = useState<Item | null | undefined>(undefined)
  const [detailsOpen, setDetailsOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [success, setSuccess] = useState<SuccessState | null>(null)
  const [touched, setTouched] = useState(false)
  const [discardOpen, setDiscardOpen] = useState(false)
  const [photoOpen, setPhotoOpen] = useState(false)

  const inventoryKeyRef = useRef(newKey())
  const inventorySignatureRef = useRef('')

  const categoriesQuery = useQuery({
    queryKey: ['category', 'list'],
    queryFn: inventoryApi.categories,
  })
  const contextItemQuery = useQuery({
    queryKey: ['item', 'detail', context.itemId],
    queryFn: () => inventoryApi.item(context.itemId ?? ''),
    enabled: Boolean(context.itemId),
  })

  const categories = categoriesQuery.data?.items ?? []
  const selectedItem = selection === undefined ? (contextItemQuery.data ?? null) : selection

  const submitMutation = useMutation({
    mutationFn: async () => {
      if (!location.roomId) throw new FormError('위치를 선택해 주세요.')

      if (mode === 'new') {
        if (!name.trim()) throw new FormError('물건 이름을 입력해 주세요.')
        const priceAmount = price.trim() ? Number(price) : null
        if (priceAmount !== null && (!Number.isInteger(priceAmount) || priceAmount < 0)) {
          throw new FormError('가격은 0원 이상의 정수로 입력해 주세요.')
        }
        const newItem = {
          name: name.trim(),
          description: description.trim() || null,
          categoryId: categoryId || null,
          priceAmount,
        }
        const signature = JSON.stringify({ newItem, ...location, status })
        if (inventorySignatureRef.current !== signature) {
          inventoryKeyRef.current = newKey()
          inventorySignatureRef.current = signature
        }
        const inventory = await inventoryApi.createInventory({
          newItem,
          ...location,
          status,
          idempotencyKey: inventoryKeyRef.current,
        })
        return { inventory, itemId: inventory.itemId, itemName: inventory.itemName }
      }

      if (!selectedItem) throw new FormError('물건을 선택해 주세요.')

      const signature = JSON.stringify({ itemId: selectedItem.id, ...location, status })
      if (inventorySignatureRef.current !== signature) {
        inventoryKeyRef.current = newKey()
        inventorySignatureRef.current = signature
      }
      const inventory = await inventoryApi.createInventory({
        itemId: selectedItem.id,
        ...location,
        status,
        idempotencyKey: inventoryKeyRef.current,
      })
      return { inventory, itemId: selectedItem.id, itemName: selectedItem.name }
    },
    onSuccess: async (data) => {
      setError(null)
      setSuccess(data)
      setTouched(false)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['inventory'] }),
        queryClient.invalidateQueries({ queryKey: ['item'] }),
        queryClient.invalidateQueries({ queryKey: ['hierarchy'] }),
      ])
    },
    onError: (cause) => {
      if (cause instanceof FormError) {
        setError(cause.message)
        return
      }
      setError(cause instanceof ApiError ? cause.message : '등록하지 못했습니다.')
    },
  })

  const canSubmit =
    !submitMutation.isPending &&
    Boolean(location.roomId) &&
    (mode === 'new' ? Boolean(name.trim()) : Boolean(selectedItem))

  function requestClose() {
    if (submitMutation.isPending) return
    if (!success && touched) {
      setDiscardOpen(true)
      return
    }
    onClose()
  }

  function switchMode(next: Mode) {
    if (next === mode) return
    setMode(next)
    setError(null)
    if (next === 'new') {
      setSelection(null)
    } else {
      setSelection(undefined)
    }
  }

  function continueAdding() {
    setSuccess(null)
    setError(null)
    setDetailsOpen(false)
    setPhotoOpen(false)
    setTouched(false)
    if (mode === 'new') {
      setName('')
      setPrice('')
      setDescription('')
      setSelection(null)
    }
    inventoryKeyRef.current = newKey()
    inventorySignatureRef.current = ''
  }

  return (
    <>
      <Dialog.Root open onOpenChange={(next) => !next && requestClose()}>
        <Dialog.Portal>
          <Dialog.Overlay className="bg-foreground/40 fixed inset-0 z-50" />
          <Dialog.Content
            className="border-border bg-background shadow-brutal fixed inset-x-0 bottom-0 z-50 flex max-h-[92dvh] flex-col border-[2.5px] sm:top-1/2 sm:right-auto sm:bottom-auto sm:left-1/2 sm:max-h-[90dvh] sm:w-[560px] sm:-translate-x-1/2 sm:-translate-y-1/2"
            onInteractOutside={(event) => {
              if (!success && touched) {
                event.preventDefault()
                setDiscardOpen(true)
              }
            }}
          >
            <div className="border-border flex items-start justify-between gap-3 border-b-[2.5px] p-4 sm:p-5">
              <div className="flex flex-col gap-1">
                <Dialog.Title className="text-xl font-black">물건 등록</Dialog.Title>
                <Dialog.Description className="text-foreground/60 text-xs">
                  사진·이름·위치만 입력하면 바로 등록할 수 있어요.
                </Dialog.Description>
              </div>
              <button
                type="button"
                aria-label="닫기"
                onClick={requestClose}
                className="border-border bg-card grid size-9 shrink-0 place-items-center border-[2.5px]"
              >
                <X aria-hidden="true" className="size-4" />
              </button>
            </div>

            <div className="min-h-0 flex-1 overflow-y-auto p-4 sm:p-5">
              {success ? (
                <div className="flex flex-col gap-4">
                  <Alert variant="success">등록했어요.</Alert>
                  <div className="border-border bg-card flex flex-col gap-1 border-[2.5px] p-4">
                    <p className="text-lg font-black">{success.itemName}</p>
                    <p className="text-foreground/70 text-xs">
                      {locationTypeLabel(success.inventory.location.type)} ·{' '}
                      {success.inventory.location.name}
                    </p>
                  </div>
                  <div className="flex flex-col gap-2 sm:flex-row">
                    <Button type="button" onClick={continueAdding} className="flex-1">
                      <Plus aria-hidden="true" className="size-4" />
                      계속 등록
                    </Button>
                    <Button asChild variant="secondary" className="flex-1">
                      <Link to={`/items/${success.itemId}`} onClick={onClose}>
                        물건 보기
                      </Link>
                    </Button>
                  </div>
                  {canEdit(success.inventory.effectivePermission) ? (
                    <div className="border-border flex flex-col gap-2 border-t-[2.5px] pt-3">
                      <Button
                        type="button"
                        variant="secondary"
                        onClick={() => setPhotoOpen((current) => !current)}
                        aria-expanded={photoOpen}
                      >
                        사진 추가 (선택)
                      </Button>
                      {photoOpen ? <InventoryImages inventoryId={success.inventory.id} /> : null}
                    </div>
                  ) : null}
                </div>
              ) : (
                <form
                  id="add-inventory-form"
                  className="flex flex-col gap-5"
                  onSubmit={(event) => {
                    event.preventDefault()
                    submitMutation.mutate()
                  }}
                >
                  <div
                    role="tablist"
                    aria-label="등록 방식"
                    className="border-border grid grid-cols-2 gap-0 border-[2.5px]"
                  >
                    <button
                      type="button"
                      role="tab"
                      aria-selected={mode === 'new'}
                      disabled={!canCreateNew}
                      onClick={() => switchMode('new')}
                      className={
                        mode === 'new'
                          ? 'bg-primary text-on-accent h-11 text-sm font-black'
                          : 'bg-card h-11 text-sm font-black disabled:opacity-40'
                      }
                    >
                      새 물건
                    </button>
                    <button
                      type="button"
                      role="tab"
                      aria-selected={mode === 'existing'}
                      onClick={() => switchMode('existing')}
                      className={
                        mode === 'existing'
                          ? 'bg-primary text-on-accent h-11 text-sm font-black'
                          : 'bg-card h-11 text-sm font-black'
                      }
                    >
                      이미 있는 물건
                    </button>
                  </div>

                  {!owner ? (
                    <p className="text-foreground/60 text-xs">
                      공유 공간에는 이미 있는 물건의 재고만 추가할 수 있어요.
                    </p>
                  ) : null}

                  {error ? <Alert variant="error">{error}</Alert> : null}

                  {mode === 'new' ? (
                    <>
                      <TextField
                        label="물건 이름"
                        value={name}
                        maxLength={120}
                        onChange={(event) => {
                          setName(event.target.value)
                          setTouched(true)
                        }}
                        autoFocus
                      />
                      <CategoryCombobox
                        value={categoryId}
                        categories={categories}
                        canCreate={owner}
                        onChange={(next) => {
                          setCategoryId(next)
                          setTouched(true)
                        }}
                      />
                    </>
                  ) : (
                    <>
                      {contextItemQuery.isLoading ? (
                        <p role="status" className="text-xs">
                          물건을 불러오는 중…
                        </p>
                      ) : null}
                      {contextItemQuery.isError ? (
                        <Alert variant="error">물건을 불러오지 못했습니다.</Alert>
                      ) : null}
                      <ItemCombobox
                        value={selectedItem}
                        onChange={(item) => {
                          setSelection(item)
                          setTouched(true)
                        }}
                      />
                    </>
                  )}

                  <LocationPicker
                    value={location}
                    defaultSource={context.source}
                    onChange={(next) => {
                      setLocation(next)
                      setTouched(true)
                    }}
                  />

                  <fieldset className="flex flex-col gap-2">
                    <legend className="text-xs font-black">상태</legend>
                    <div className="grid grid-cols-2 gap-2">
                      {(
                        [
                          ['STORED', '보관'],
                          ['IN_USE', '사용 중'],
                        ] as const
                      ).map(([value, label]) => (
                        <button
                          key={value}
                          type="button"
                          aria-pressed={status === value}
                          onClick={() => {
                            setStatus(value)
                            setTouched(true)
                          }}
                          className={
                            status === value
                              ? 'border-border bg-primary text-on-accent h-11 border-[2.5px] text-sm font-black'
                              : 'border-border bg-card hover:bg-sunken h-11 border-[2.5px] text-sm font-black'
                          }
                        >
                          {label}
                        </button>
                      ))}
                    </div>
                  </fieldset>

                  <div className="flex flex-col gap-3">
                    <button
                      type="button"
                      onClick={() => setDetailsOpen((current) => !current)}
                      aria-expanded={detailsOpen}
                      className="text-foreground/70 flex items-center gap-1 text-left text-xs font-black"
                    >
                      <ChevronDown
                        aria-hidden="true"
                        className={detailsOpen ? 'size-4 rotate-180' : 'size-4'}
                      />
                      가격·설명 {detailsOpen ? '접기' : '추가'}
                    </button>
                    {detailsOpen ? (
                      <>
                        <TextField
                          label="가격 (선택)"
                          type="number"
                          min={0}
                          inputMode="numeric"
                          value={price}
                            onChange={(event) => {
                            setPrice(event.target.value)
                            setTouched(true)
                          }}
                        />
                        <label className="flex flex-col gap-1.5">
                          <span className="text-xs font-black">설명 (선택)</span>
                          <textarea
                            value={description}
                            maxLength={2000}
                                onChange={(event) => {
                              setDescription(event.target.value)
                              setTouched(true)
                            }}
                            rows={3}
                            className="border-border bg-card min-h-20 w-full border-[2.5px] px-3 py-2 text-sm font-medium outline-none disabled:opacity-50"
                          />
                        </label>
                      </>
                    ) : null}
                  </div>
                </form>
              )}
            </div>

            {!success ? (
              <div className="border-border flex items-center justify-end gap-2 border-t-[2.5px] p-4 sm:p-5">
                <Button type="button" variant="secondary" onClick={requestClose}>
                  취소
                </Button>
                <Button
                  type="submit"
                  form="add-inventory-form"
                  disabled={!canSubmit}
                  aria-busy={submitMutation.isPending}
                >
                  {submitMutation.isPending ? '등록 중…' : '등록'}
                </Button>
              </div>
            ) : null}
          </Dialog.Content>
        </Dialog.Portal>
      </Dialog.Root>

      <ConfirmDialog
        open={discardOpen}
        onOpenChange={setDiscardOpen}
        title="입력을 취소할까요?"
        description="입력한 내용이 사라져요."
        confirmLabel="취소하고 닫기"
        onConfirm={() => {
          setDiscardOpen(false)
          onClose()
        }}
      />
    </>
  )
}
