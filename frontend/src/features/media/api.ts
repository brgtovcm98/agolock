import { apiFetch } from '@/shared/api/client'

export type ImageStatus =
  'UPLOADING' | 'DRAFT' | 'PENDING' | 'PROCESSING' | 'READY' | 'FAILED' | 'DELETED'

export type ImageTargetType = 'ITEM' | 'INVENTORY'

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

function targetPath(targetType: ImageTargetType, targetId: string) {
  return targetType === 'ITEM' ? `/items/${targetId}/images` : `/inventories/${targetId}/images`
}

export const mediaApi = {
  images: (targetType: ImageTargetType, targetId: string) =>
    apiFetch<{ items: ImageItem[] }>(targetPath(targetType, targetId)),
  inventoryImages: (inventoryId: string) =>
    apiFetch<{ items: ImageItem[] }>(`/inventories/${inventoryId}/images`),
  itemImages: (itemId: string) => apiFetch<{ items: ImageItem[] }>(`/items/${itemId}/images`),
  presign: (targetType: ImageTargetType, targetId: string, sizeBytes: number) =>
    apiFetch<PresignResponse>('/images/presign', {
      method: 'POST',
      body: JSON.stringify({
        targetType,
        targetId,
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
