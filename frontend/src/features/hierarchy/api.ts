import { apiFetch } from '@/shared/api/client'

export type Permission = 'NONE' | 'VIEW' | 'EDIT' | 'MANAGE' | 'OWNER'

export type Room = {
  id: string
  name: string
  description: string | null
  shelfCount: number
  inventoryCount: number
  version: number
  effectivePermission: Permission
  isShared: boolean
  createdAt: string
  updatedAt: string
}

export type Shelf = {
  id: string
  roomId: string
  name: string
  description: string | null
  boxCount: number
  inventoryCount: number
  version: number
  effectivePermission: Permission
  isShared: boolean
  createdAt: string
  updatedAt: string
}

export type Box = {
  id: string
  shelfId: string
  name: string
  description: string | null
  inventoryCount: number
  version: number
  effectivePermission: Permission
  isShared: boolean
  createdAt: string
  updatedAt: string
}

export type Page<T> = {
  items: T[]
  nextCursor: string | null
}

export type CreateNodeInput = {
  name: string
  description?: string | null
}

export type UpdateNodeInput = {
  name?: string
  description?: string | null
  version: number
}

function query(params: Record<string, string | number | undefined>) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== '') search.set(key, String(value))
  })
  const suffix = search.toString()
  return suffix ? `?${suffix}` : ''
}

export const hierarchyApi = {
  listRooms: (cursor?: string, limit = 20) =>
    apiFetch<Page<Room>>(`/rooms${query({ cursor, limit })}`),
  createRoom: (input: CreateNodeInput) =>
    apiFetch<Room>('/rooms', { method: 'POST', body: JSON.stringify(input) }),
  getRoom: (roomId: string) => apiFetch<Room>(`/rooms/${roomId}`),
  updateRoom: (roomId: string, input: UpdateNodeInput) =>
    apiFetch<Room>(`/rooms/${roomId}`, { method: 'PATCH', body: JSON.stringify(input) }),
  deleteRoom: (roomId: string) => apiFetch<void>(`/rooms/${roomId}`, { method: 'DELETE' }),

  listShelves: (roomId: string, cursor?: string, limit = 20) =>
    apiFetch<Page<Shelf>>(`/rooms/${roomId}/shelves${query({ cursor, limit })}`),
  createShelf: (roomId: string, input: CreateNodeInput) =>
    apiFetch<Shelf>(`/rooms/${roomId}/shelves`, { method: 'POST', body: JSON.stringify(input) }),
  getShelf: (shelfId: string) => apiFetch<Shelf>(`/shelves/${shelfId}`),
  updateShelf: (shelfId: string, input: UpdateNodeInput) =>
    apiFetch<Shelf>(`/shelves/${shelfId}`, { method: 'PATCH', body: JSON.stringify(input) }),
  moveShelf: (shelfId: string, roomId: string, version: number) =>
    apiFetch<Shelf>(`/shelves/${shelfId}/parent`, {
      method: 'PATCH',
      body: JSON.stringify({ roomId, version }),
    }),
  deleteShelf: (shelfId: string) => apiFetch<void>(`/shelves/${shelfId}`, { method: 'DELETE' }),

  listBoxes: (shelfId: string, cursor?: string, limit = 20) =>
    apiFetch<Page<Box>>(`/shelves/${shelfId}/boxes${query({ cursor, limit })}`),
  createBox: (shelfId: string, input: CreateNodeInput) =>
    apiFetch<Box>(`/shelves/${shelfId}/boxes`, { method: 'POST', body: JSON.stringify(input) }),
  getBox: (boxId: string) => apiFetch<Box>(`/boxes/${boxId}`),
  updateBox: (boxId: string, input: UpdateNodeInput) =>
    apiFetch<Box>(`/boxes/${boxId}`, { method: 'PATCH', body: JSON.stringify(input) }),
  moveBox: (boxId: string, shelfId: string, version: number) =>
    apiFetch<Box>(`/boxes/${boxId}/parent`, {
      method: 'PATCH',
      body: JSON.stringify({ shelfId, version }),
    }),
  deleteBox: (boxId: string) => apiFetch<void>(`/boxes/${boxId}`, { method: 'DELETE' }),
}
