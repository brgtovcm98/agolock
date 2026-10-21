import type { Inventory, Item } from '@/features/inventory/api'
import type { Box, Room, Shelf } from '@/features/hierarchy/api'

export const testCategory = { id: 'c1', name: '공구', color: null, sortOrder: 0 }

export function testInventory(overrides: Partial<Inventory> = {}): Inventory {
  return {
    id: 'v1',
    itemId: 'i1',
    itemName: '전동 드릴 18V',
    displayName: '전동 드릴 18V',
    description: '배터리 2개 포함',
    categoryId: 'c1',
    priceAmount: 129000,
    status: 'IN_USE',
    location: {
      type: 'BOX',
      roomId: 'r1',
      shelfId: 's1',
      boxId: 'b1',
      name: '파란 박스',
    },
    nameOverride: null,
    descriptionOverride: null,
    categoryIdOverride: null,
    priceAmountOverride: null,
    version: 2,
    effectivePermission: 'OWNER',
    isShared: false,
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-02T00:00:00Z',
    ...overrides,
  }
}

export function testItem(overrides: Partial<Item> = {}): Item {
  return {
    id: 'i1',
    name: '전동 드릴 18V',
    description: '배터리 2개 포함',
    categoryId: 'c1',
    priceAmount: 129000,
    currency: 'KRW',
    version: 1,
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
    effectivePermission: 'OWNER',
    isShared: false,
    ...overrides,
  }
}

export function testRoom(overrides: Partial<Room> = {}): Room {
  return {
    id: 'r1',
    name: '거실',
    description: null,
    shelfCount: 1,
    inventoryCount: 1,
    version: 1,
    effectivePermission: 'OWNER',
    isShared: false,
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
    ...overrides,
  }
}

export function testShelf(overrides: Partial<Shelf> = {}): Shelf {
  return {
    id: 's1',
    roomId: 'r1',
    name: '공구함',
    description: null,
    boxCount: 1,
    inventoryCount: 0,
    version: 1,
    effectivePermission: 'OWNER',
    isShared: false,
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
    ...overrides,
  }
}

export function testBox(overrides: Partial<Box> = {}): Box {
  return {
    id: 'b1',
    shelfId: 's1',
    name: '파란 박스',
    description: null,
    inventoryCount: 1,
    version: 1,
    effectivePermission: 'OWNER',
    isShared: false,
    createdAt: '2026-10-01T00:00:00Z',
    updatedAt: '2026-10-01T00:00:00Z',
    ...overrides,
  }
}

export function inventoryListKey(statuses: Inventory['status'][] = []) {
  return ['inventory', 'list', { scope: 'ALL', statuses }] as const
}
