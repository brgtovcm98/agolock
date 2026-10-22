import { apiFetch } from '@/shared/api/client'

export type InventoryStatus = 'IN_USE' | 'STORED' | 'DISPOSED'
export type Permission = 'NONE' | 'VIEW' | 'EDIT' | 'MANAGE' | 'OWNER'
export type InventoryScope = 'OWNED' | 'SHARED' | 'ALL'

export type Page<T> = {
  items: T[]
  nextCursor: string | null
}

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
  effectivePermission?: Permission
  isShared?: boolean
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
  effectivePermission: Permission
  isShared: boolean
  createdAt: string
  updatedAt: string
}

export type Movement = {
  id: string
  inventoryId: string
  from: InventoryLocation
  to: InventoryLocation
  movedAt: string
}

export type ItemUpdateImpact = {
  affectedInventoryCount: number
  protectedByOverrideCount: number
}

export type CategoryMergeResult = {
  mergedItemCount: number
  mergedInventoryCount: number
}

export type ItemQuery = {
  q?: string
  limit?: number
  cursor?: string
  scope?: InventoryScope
}

export type InventoryQuery = {
  statuses?: InventoryStatus[]
  scope?: InventoryScope
  limit?: number
  cursor?: string
  roomId?: string
  shelfId?: string
  boxId?: string
}

export type CreateItemInput = {
  name: string
  description?: string | null
  categoryId?: string | null
  priceAmount?: number | null
  idempotencyKey?: string
}

export type LocationInput = {
  roomId?: string | null
  shelfId?: string | null
  boxId?: string | null
}

export type NewItemInput = {
  name: string
  description?: string | null
  categoryId?: string | null
  priceAmount?: number | null
}

export type CreateInventoryInput = LocationInput & {
  itemId?: string
  newItem?: NewItemInput
  status?: InventoryStatus
  idempotencyKey?: string
}

export type UpdateInventoryInput = {
  version: number
  status?: InventoryStatus
  confirmDisposal?: boolean
  nameOverride?: string | null
  descriptionOverride?: string | null
  categoryIdOverride?: string | null
  priceAmountOverride?: number | null
}

export type MoveInventoryInput = LocationInput & {
  version: number
}

export type UpdateItemInput = {
  version: number
  name: string
  description: string | null
  categoryId: string | null
  priceAmount: number | null
}

export type UpdateCategoryInput = {
  name?: string
  color?: string | null
  sortOrder?: number
}

function queryString(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value))
  })
  const suffix = search.toString()
  return suffix ? `?${suffix}` : ''
}

export const inventoryApi = {
  categories: () => apiFetch<{ items: Category[] }>('/categories'),
  createCategory: (name: string) =>
    apiFetch<Category>('/categories', { method: 'POST', body: JSON.stringify({ name }) }),
  updateCategory: (categoryId: string, input: UpdateCategoryInput) =>
    apiFetch<Category>(`/categories/${categoryId}`, {
      method: 'PATCH',
      body: JSON.stringify(input),
    }),
  mergeCategory: (categoryId: string, targetCategoryId: string) =>
    apiFetch<CategoryMergeResult>(`/categories/${categoryId}/merge`, {
      method: 'POST',
      body: JSON.stringify({ targetCategoryId }),
    }),
  deleteCategory: (categoryId: string) =>
    apiFetch<void>(`/categories/${categoryId}`, { method: 'DELETE' }),

  items: (params: ItemQuery = {}) =>
    apiFetch<Page<Item>>(
      `/items${queryString({ q: params.q, limit: params.limit ?? 20, cursor: params.cursor, scope: params.scope })}`,
    ),
  item: (itemId: string) => apiFetch<Item>(`/items/${itemId}`),
  itemInventories: (itemId: string) => apiFetch<Page<Inventory>>(`/items/${itemId}/inventories`),
  itemUpdateImpact: (itemId: string) =>
    apiFetch<ItemUpdateImpact>(`/items/${itemId}/update-impact`),
  updateItem: (itemId: string, input: UpdateItemInput) =>
    apiFetch<Item>(`/items/${itemId}`, {
      method: 'PATCH',
      body: JSON.stringify(input),
    }),
  createItem: (input: CreateItemInput) =>
    apiFetch<Item>('/items', {
      method: 'POST',
      headers: input.idempotencyKey ? { 'Idempotency-Key': input.idempotencyKey } : undefined,
      body: JSON.stringify({
        name: input.name,
        description: input.description ?? null,
        categoryId: input.categoryId ?? null,
        priceAmount: input.priceAmount ?? null,
      }),
    }),

  inventories: (params: InventoryQuery = {}) => {
    const search = new URLSearchParams()
    params.statuses?.forEach((status) => search.append('status', status))
    if (params.scope) search.set('scope', params.scope)
    if (params.roomId) search.set('roomId', params.roomId)
    if (params.shelfId) search.set('shelfId', params.shelfId)
    if (params.boxId) search.set('boxId', params.boxId)
    search.set('limit', String(params.limit ?? 20))
    if (params.cursor) search.set('cursor', params.cursor)
    return apiFetch<Page<Inventory>>(`/inventories?${search.toString()}`)
  },
  inventory: (inventoryId: string) => apiFetch<Inventory>(`/inventories/${inventoryId}`),
  movements: (inventoryId: string) => apiFetch<Movement[]>(`/inventories/${inventoryId}/movements`),
  createInventory: (input: CreateInventoryInput) =>
    apiFetch<Inventory>('/inventories', {
      method: 'POST',
      headers: input.idempotencyKey ? { 'Idempotency-Key': input.idempotencyKey } : undefined,
      body: JSON.stringify({
        itemId: input.itemId ?? null,
        newItem: input.newItem ?? null,
        roomId: input.roomId ?? null,
        shelfId: input.shelfId ?? null,
        boxId: input.boxId ?? null,
        status: input.status ?? 'STORED',
      }),
    }),
  updateInventory: (inventoryId: string, input: UpdateInventoryInput) =>
    apiFetch<Inventory>(`/inventories/${inventoryId}`, {
      method: 'PATCH',
      body: JSON.stringify(input),
    }),
  moveInventory: (inventoryId: string, input: MoveInventoryInput) =>
    apiFetch<Inventory>(`/inventories/${inventoryId}/location`, {
      method: 'PATCH',
      body: JSON.stringify(input),
    }),
  deleteInventory: (inventoryId: string) =>
    apiFetch<void>(`/inventories/${inventoryId}`, { method: 'DELETE' }),
}
