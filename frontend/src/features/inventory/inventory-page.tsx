import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { PackagePlus, Plus } from 'lucide-react'
import { useState } from 'react'
import { inventoryApi, type InventoryStatus } from './api'
import { ApiError } from '@/shared/api/client'
import { Alert } from '@/shared/ui/alert'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { MetaText, PageTitle, SectionTitle } from '@/shared/ui/typography'
import { TextField } from '@/shared/ui/text-field'

const statusLabels: Record<InventoryStatus, string> = {
  IN_USE: '사용 중',
  STORED: '보관 중',
  DISPOSED: '폐기',
}

export function InventoryPage() {
  const queryClient = useQueryClient()
  const [categoryName, setCategoryName] = useState('')
  const [itemName, setItemName] = useState('')
  const [itemPrice, setItemPrice] = useState('')
  const [itemCategory, setItemCategory] = useState('')
  const [selectedItem, setSelectedItem] = useState('')
  const [selectedRoom, setSelectedRoom] = useState('')
  const [error, setError] = useState<string | null>(null)

  const categoriesQuery = useQuery({
    queryKey: ['inventory', 'categories'],
    queryFn: inventoryApi.categories,
  })
  const itemsQuery = useQuery({ queryKey: ['inventory', 'items'], queryFn: inventoryApi.items })
  const inventoriesQuery = useQuery({
    queryKey: ['inventory', 'list'],
    queryFn: () => inventoryApi.inventories(),
  })
  const roomsQuery = useQuery({
    queryKey: ['hierarchy', 'rooms', 'inventory-picker'],
    queryFn: inventoryApi.rooms,
  })

  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['inventory'] })
  const fail = (cause: unknown) =>
    setError(cause instanceof ApiError ? cause.message : '요청을 처리하지 못했습니다.')

  const categoryMutation = useMutation({
    mutationFn: () => inventoryApi.createCategory(categoryName),
    onSuccess: async () => {
      setCategoryName('')
      setError(null)
      await invalidate()
    },
    onError: fail,
  })
  const itemMutation = useMutation({
    mutationFn: () =>
      inventoryApi.createItem(itemName, itemPrice ? Number(itemPrice) : null, itemCategory || null),
    onSuccess: async () => {
      setItemName('')
      setItemPrice('')
      setError(null)
      await invalidate()
    },
    onError: fail,
  })
  const inventoryMutation = useMutation({
    mutationFn: () => inventoryApi.createInventory(selectedItem, selectedRoom),
    onSuccess: async () => {
      setSelectedItem('')
      setSelectedRoom('')
      setError(null)
      await invalidate()
    },
    onError: fail,
  })

  const categories = categoriesQuery.data?.items ?? []
  const items = itemsQuery.data?.items ?? []
  const inventories = inventoriesQuery.data?.items ?? []
  const rooms = roomsQuery.data?.items ?? []

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-1">
        <p className="label-caps text-foreground/60">SCR-008 · 재고</p>
        <PageTitle>재고</PageTitle>
        <MetaText>카테고리와 Item을 만들고, Room에 첫 Inventory를 등록합니다.</MetaText>
      </div>

      {error ? <Alert variant="error">{error}</Alert> : null}

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardBody className="gap-3">
            <SectionTitle>카테고리</SectionTitle>
            <div className="flex flex-wrap gap-2">
              {categories.map((category) => (
                <Badge key={category.id} variant="neutral">
                  {category.name}
                </Badge>
              ))}
              {categories.length === 0 ? (
                <span className="text-xs">아직 카테고리가 없습니다.</span>
              ) : null}
            </div>
            <form
              className="flex items-end gap-2"
              onSubmit={(event) => {
                event.preventDefault()
                if (categoryName.trim()) categoryMutation.mutate()
              }}
            >
              <TextField
                label="새 카테고리"
                value={categoryName}
                onChange={(event) => setCategoryName(event.target.value)}
              />
              <Button type="submit" disabled={categoryMutation.isPending || !categoryName.trim()}>
                <Plus aria-hidden="true" className="size-4" />
                추가
              </Button>
            </form>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="gap-3">
            <SectionTitle>Item</SectionTitle>
            <div className="flex flex-col gap-2">
              {items.map((item) => (
                <div
                  key={item.id}
                  className="border-border bg-card flex items-center justify-between border-2 px-3 py-2"
                >
                  <span className="text-sm font-black">{item.name}</span>
                  <span className="text-xs">
                    {item.priceAmount ? `${item.priceAmount.toLocaleString()}원` : '가격 없음'}
                  </span>
                </div>
              ))}
              {items.length === 0 ? <span className="text-xs">아직 Item이 없습니다.</span> : null}
            </div>
            <form
              className="grid gap-2 md:grid-cols-2"
              onSubmit={(event) => {
                event.preventDefault()
                if (itemName.trim()) itemMutation.mutate()
              }}
            >
              <TextField
                label="Item 이름"
                value={itemName}
                onChange={(event) => setItemName(event.target.value)}
              />
              <TextField
                label="가격"
                type="number"
                value={itemPrice}
                onChange={(event) => setItemPrice(event.target.value)}
              />
              <label className="flex flex-col gap-1.5 md:col-span-2">
                <span className="text-xs font-black">카테고리</span>
                <select
                  value={itemCategory}
                  onChange={(event) => setItemCategory(event.target.value)}
                  className="border-border bg-card h-12 border-[2.5px] px-3 text-sm"
                >
                  <option value="">없음</option>
                  {categories.map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
                </select>
              </label>
              <Button
                type="submit"
                disabled={itemMutation.isPending || !itemName.trim()}
                className="md:col-span-2"
              >
                Item 추가
              </Button>
            </form>
          </CardBody>
        </Card>
      </div>

      <Card>
        <CardBody className="gap-3">
          <div className="flex flex-wrap items-center justify-between gap-2">
            <SectionTitle>Inventory</SectionTitle>
            <Badge variant="info">{inventories.length}개</Badge>
          </div>
          <form
            className="grid gap-2 md:grid-cols-[1fr_1fr_auto]"
            onSubmit={(event) => {
              event.preventDefault()
              if (selectedItem && selectedRoom) inventoryMutation.mutate()
            }}
          >
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-black">Item</span>
              <select
                value={selectedItem}
                onChange={(event) => setSelectedItem(event.target.value)}
                className="border-border bg-card h-12 border-[2.5px] px-3 text-sm"
              >
                <option value="">선택</option>
                {items.map((item) => (
                  <option key={item.id} value={item.id}>
                    {item.name}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-black">Room</span>
              <select
                value={selectedRoom}
                onChange={(event) => setSelectedRoom(event.target.value)}
                className="border-border bg-card h-12 border-[2.5px] px-3 text-sm"
              >
                <option value="">선택</option>
                {rooms.map((room) => (
                  <option key={room.id} value={room.id}>
                    {room.name}
                  </option>
                ))}
              </select>
            </label>
            <Button
              type="submit"
              disabled={inventoryMutation.isPending || !selectedItem || !selectedRoom}
              className="self-end"
            >
              <PackagePlus aria-hidden="true" className="size-4" />
              Inventory 추가
            </Button>
          </form>

          <div className="grid gap-2 md:grid-cols-2">
            {inventories.map((inventory) => (
              <div
                key={inventory.id}
                className="border-border bg-card flex flex-col gap-1 border-[2.5px] p-3"
              >
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-black">{inventory.displayName}</span>
                  <Badge variant="neutral">{statusLabels[inventory.status]}</Badge>
                </div>
                <span className="text-xs">
                  {inventory.location.type} · {inventory.location.name}
                </span>
              </div>
            ))}
            {inventories.length === 0 ? (
              <p className="text-xs">아직 Inventory가 없습니다.</p>
            ) : null}
          </div>
        </CardBody>
      </Card>
    </div>
  )
}
