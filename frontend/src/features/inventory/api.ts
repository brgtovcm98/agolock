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
  version: number
  changedFields: string[]
  totalInventoryCount: number
  fullyProtectedInventoryCount: number
  fieldImpacts: Array<{
    field: string
    affectedInventoryCount: number
    protectedInventoryCount: number
  }>
}

export type CategoryMergeResult = {
  mergedItemCount: number
  mergedInventoryCount: number
}

export type ItemQuery = LocationInput & {
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
  name?: string
  description?: string | null
  categoryId?: string | null
  priceAmount?: number | null
}

export type UpdateCategoryInput = {
  name?: string
  color?: string | null
  sortOrder?: number
}

function queryString(params: Record<string, string | number | null | undefined>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  const suffix = search.toString()
  return suffix ? `?${suffix}` : ''
}

export function wireLocation(input: LocationInput): {
  roomId?: string
  shelfId?: string
  boxId?: string
} {
  if (input.boxId) return { boxId: input.boxId }
  if (input.shelfId) return { shelfId: input.shelfId }
  if (input.roomId) return { roomId: input.roomId }
  return {}
}
export type LocationTarget = { type: 'ROOM' | 'SHELF' | 'BOX'; id: string; path: string }
export type PageQuery = { limit?: number; cursor?: string; q?: string }
export async function collectPages<T>(
  fetchPage: (cursor?: string) => Promise<Page<T>>,
): Promise<Page<T>> {
  const items: T[] = []
  let cursor: string | undefined
  do {
    const page = await fetchPage(cursor)
    items.push(...page.items)
    cursor = page.nextCursor ?? undefined
  } while (cursor)
  return { items, nextCursor: null }
}
export const inventoryApi = {
  categories: (params: PageQuery & { itemId?: string } = {}) =>
    apiFetch<Page<Category>>(
      `/categories${queryString({ itemId: params.itemId, limit: params.limit ?? 100, cursor: params.cursor })}`,
    ),
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
      `/items${queryString({ q: params.q, limit: params.limit ?? 20, cursor: params.cursor, scope: params.scope, ...wireLocation(params) })}`,
    ),
  item: (itemId: string) => apiFetch<Item>(`/items/${itemId}`),
  itemInventories: (itemId: string, params: PageQuery & { status?: InventoryStatus } = {}) =>
    apiFetch<Page<Inventory>>(
      `/items/${itemId}/inventories${queryString({ status: params.status, limit: params.limit ?? 20, cursor: params.cursor })}`,
    ),
  inventoryTargets: (itemId: string, params: PageQuery = {}) =>
    apiFetch<Page<LocationTarget>>(
      `/items/${itemId}/inventory-targets${queryString({ q: params.q, limit: params.limit ?? 100, cursor: params.cursor })}`,
    ),
  moveTargets: (inventoryId: string, params: PageQuery = {}) =>
    apiFetch<Page<LocationTarget>>(
      `/inventories/${inventoryId}/move-targets${queryString({ q: params.q, limit: params.limit ?? 100, cursor: params.cursor })}`,
    ),
  itemUpdateImpact: (itemId: string, input: UpdateItemInput) =>
    apiFetch<ItemUpdateImpact>(`/items/${itemId}/update-impact`, {
      method: 'POST',
      body: JSON.stringify(input),
    }),
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
    Object.entries(wireLocation(params)).forEach(([key, value]) => {
      if (value) search.set(key, value)
    })
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
        ...wireLocation(input),
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
      body: JSON.stringify({ version: input.version, ...wireLocation(input) }),
    }),
  deleteInventory: (inventoryId: string, version: number) =>
    apiFetch<void>(`/inventories/${inventoryId}?version=${version}`, { method: 'DELETE' }),
}
