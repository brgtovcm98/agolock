import { apiFetch } from '@/shared/api/client'
import type { InventoryStatus } from '@/features/inventory/api'

export type SearchInventory = {
  inventoryId: string
  effectiveName: string
  status: InventoryStatus
  locationPath: string
  shared: boolean
  ownerName: string | null
}

export type SearchItemGroup = {
  itemId: string
  itemName: string
  matchedInventoryCount: number
  inventoryCount: number
  hasMoreInventories: boolean
  primaryImageId: string | null
  effectiveCategory: { id: string; name: string; color: string | null } | null
  inventories: SearchInventory[]
}

export type SearchResponse = {
  items: SearchItemGroup[]
  nextCursor: string | null
}

export type RecentSearch = {
  id: string
  query: string
  lastSearchedAt: string
}

export type FrequentItem = {
  itemId: string
  itemName: string
  primaryImageId: string | null
  viewCount: number
  lastViewedAt: string
}

export type SearchFilters = {
  statuses: InventoryStatus[]
  scope: 'ALL' | 'OWNED' | 'SHARED'
  sort: 'RELEVANCE' | 'UPDATED_DESC' | 'NAME_ASC' | 'VIEWED_DESC' | 'STATUS_ASC'
  hasImage: boolean | null
  categoryId?: string
}

function queryString(params: Record<string, string | number | boolean | string[] | undefined>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value === undefined || value === '' || value === null) return
    if (Array.isArray(value)) {
      value.forEach((item) => search.append(key, item))
    } else {
      search.set(key, String(value))
    }
  })
  return search.toString()
}

export const searchApi = {
  search: (q: string, filters: SearchFilters, cursor?: string) =>
    apiFetch<SearchResponse>(
      `/search?${queryString({
        q: q || undefined,
        status: filters.statuses.length > 0 ? filters.statuses : undefined,
        scope: filters.scope,
        sort: filters.sort,
        hasImage: filters.hasImage ?? undefined,
        categoryId: filters.categoryId,
        cursor,
        limit: 20,
      })}`,
    ),
  recent: () => apiFetch<{ items: RecentSearch[] }>('/search/recent'),
  clearRecent: () => apiFetch<void>('/search/recent', { method: 'DELETE' }),
  frequent: () => apiFetch<{ items: FrequentItem[] }>('/search/frequent'),
  recordView: (itemId: string) => apiFetch<void>(`/items/${itemId}/view`, { method: 'POST' }),
}
