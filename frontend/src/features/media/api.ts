import { apiFetch } from '@/shared/api/client'

export type ImageStatus =
  'UPLOADING' | 'DRAFT' | 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED' | 'DELETED'

export type ImageItem = {
  id: string
  status: ImageStatus
  itemId: string | null
  inventoryId: string | null
  mimeType: string | null
  sizeBytes: number | null
  isPrimary: boolean
  createdAt: string
}

type PresignResponse = {
  imageId: string
  uploadUrl: string
  objectKey: string
  expiresAt: string
  uploadExpiresAt: string
}

export const mediaApi = {
  inventoryImages: (inventoryId: string) =>
    apiFetch<{ items: ImageItem[] }>(`/inventories/${inventoryId}/images`),
  presign: (inventoryId: string, sizeBytes: number) =>
    apiFetch<PresignResponse>('/images/presign', {
      method: 'POST',
      body: JSON.stringify({
        targetType: 'INVENTORY',
        targetId: inventoryId,
        contentType: 'image/webp',
        sizeBytes,
      }),
    }),
  complete: (imageId: string, objectKey: string) =>
    apiFetch<ImageItem>(`/images/${imageId}/complete`, {
      method: 'POST',
      body: JSON.stringify({ objectKey }),
    }),
  url: (imageId: string) => apiFetch<{ url: string }>(`/images/${imageId}/url`),
  primary: (imageId: string) =>
    apiFetch<ImageItem>(`/images/${imageId}/primary`, { method: 'POST' }),
  delete: (imageId: string) => apiFetch<void>(`/images/${imageId}`, { method: 'DELETE' }),
}
