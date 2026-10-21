import { apiFetch } from '@/shared/api/client'

export type InventoryStatus = 'IN_USE' | 'STORED' | 'DISPOSED'

export type Category = {
  id: string
  name: string
  color: string | null
  sortOrder: number
}

export type Item = {
  id: string
  name: string
  description: string | null
  categoryId: string | null
  priceAmount: number | null
  currency: string
  version: number
  createdAt: string
  updatedAt: string
  primaryImageId?: string | null
}

export type InventoryLocation = {
  type: 'ROOM' | 'SHELF' | 'BOX' | 'UNKNOWN'
  roomId: string | null
  shelfId: string | null
  boxId: string | null
  name: string
}

export type Inventory = {
  id: string
  itemId: string
  itemName: string
  displayName: string
  description: string | null
  categoryId: string | null
  priceAmount: number | null
  status: InventoryStatus
  location: InventoryLocation
  nameOverride: string | null
  descriptionOverride: string | null
  categoryIdOverride: string | null
  priceAmountOverride: number | null
  version: number
  effectivePermission: string
  isShared: boolean
  createdAt: string
  updatedAt: string
  primaryImageId?: string | null
}

export type Room = { id: string; name: string }

export const inventoryApi = {
  categories: () => apiFetch<{ items: Category[] }>('/categories'),
  createCategory: (name: string) =>
    apiFetch<Category>('/categories', { method: 'POST', body: JSON.stringify({ name }) }),
  items: () => apiFetch<{ items: Item[] }>('/items?limit=100'),
  createItem: (name: string, priceAmount: number | null, categoryId: string | null) =>
    apiFetch<Item>('/items', {
      method: 'POST',
      body: JSON.stringify({ name, priceAmount, categoryId }),
    }),
  inventories: (status?: InventoryStatus) =>
    apiFetch<{ items: Inventory[] }>(`/inventories${status ? `?status=${status}` : ''}`),
  createInventory: (itemId: string, roomId: string) =>
    apiFetch<Inventory>('/inventories', {
      method: 'POST',
      body: JSON.stringify({ itemId, roomId }),
    }),
  rooms: () => apiFetch<{ items: Room[] }>('/rooms?limit=100'),
}
