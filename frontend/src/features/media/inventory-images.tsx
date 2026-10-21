import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { ImagePlus, Star, Trash2 } from 'lucide-react'
import { useRef, useState } from 'react'
import { ApiError } from '@/shared/api/client'
import { Alert } from '@/shared/ui/alert'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { mediaApi, type ImageStatus } from './api'

const statusLabel: Record<ImageStatus, string> = {
  UPLOADING: '업로드 중',
  DRAFT: '임시',
  PENDING: '처리 대기',
  PROCESSING: '처리 중',
  READY: '준비됨',
  FAILED: '실패',
  DELETED: '삭제됨',
}

export function InventoryImages({ inventoryId }: { inventoryId: string }) {
  const queryClient = useQueryClient()
  const inputRef = useRef<HTMLInputElement>(null)
  const [error, setError] = useState<string | null>(null)
  const imagesQuery = useQuery({
    queryKey: ['images', 'inventory', inventoryId],
    queryFn: () => mediaApi.inventoryImages(inventoryId),
  })
  const images = imagesQuery.data?.items ?? []
  const ready = images.filter((image) => image.status === 'READY')
  const urls = useQueries({
    queries: ready.map((image) => ({
      queryKey: ['images', 'url', image.id],
      queryFn: () => mediaApi.url(image.id),
      staleTime: 4 * 60 * 1000,
    })),
  })
  const urlMap = new Map(ready.map((image, index) => [image.id, urls[index]?.data?.url]))

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey: ['images', 'inventory', inventoryId] })
    await queryClient.invalidateQueries({ queryKey: ['inventory'] })
  }
  const fail = (cause: unknown) =>
    setError(cause instanceof ApiError ? cause.message : '이미지 요청을 처리하지 못했습니다.')

  const uploadMutation = useMutation({
    mutationFn: async (file: File) => {
      const presigned = await mediaApi.presign(inventoryId, file.size)
      const response = await fetch(presigned.uploadUrl, {
        method: 'PUT',
        headers: { 'Content-Type': 'image/webp' },
        body: file,
      })
      if (!response.ok) throw new Error('업로드에 실패했습니다.')
      await mediaApi.complete(presigned.imageId, presigned.objectKey)
    },
    onSuccess: refresh,
    onError: fail,
  })
  const primaryMutation = useMutation({
    mutationFn: mediaApi.primary,
    onSuccess: refresh,
    onError: fail,
  })
  const deleteMutation = useMutation({
    mutationFn: mediaApi.delete,
    onSuccess: refresh,
    onError: fail,
  })

  return (
    <div className="border-border flex flex-col gap-2 border-t-2 pt-2">
      <div className="flex items-center justify-between gap-2">
        <span className="text-xs font-black">이미지</span>
        <input
          ref={inputRef}
          type="file"
          accept="image/webp"
          className="hidden"
          onChange={(event) => {
            const file = event.target.files?.[0]
            if (file) uploadMutation.mutate(file)
            event.target.value = ''
          }}
        />
        <Button
          type="button"
          size="sm"
          variant="secondary"
          disabled={uploadMutation.isPending}
          onClick={() => inputRef.current?.click()}
        >
          <ImagePlus aria-hidden="true" className="size-4" />
          WebP 추가
        </Button>
      </div>
      {error ? <Alert variant="error">{error}</Alert> : null}
      <div className="flex flex-wrap gap-2">
        {images.map((image) => (
          <div key={image.id} className="border-border flex w-28 flex-col gap-1 border-2 p-1">
            {image.status === 'READY' && urlMap.get(image.id) ? (
              <img
                src={urlMap.get(image.id)}
                alt=""
                className="aspect-square w-full object-cover"
              />
            ) : (
              <div className="bg-sunken flex aspect-square items-center justify-center text-[10px]">
                {statusLabel[image.status]}
              </div>
            )}
            <div className="flex items-center justify-between">
              <Badge variant={image.status === 'FAILED' ? 'dis' : 'neutral'}>
                {statusLabel[image.status]}
              </Badge>
              <div className="flex">
                <button
                  type="button"
                  aria-label="대표 이미지"
                  disabled={image.status !== 'READY' || image.isPrimary}
                  onClick={() => primaryMutation.mutate(image.id)}
                >
                  <Star aria-hidden="true" className="size-3" />
                </button>
                <button
                  type="button"
                  aria-label="이미지 삭제"
                  onClick={() => deleteMutation.mutate(image.id)}
                >
                  <Trash2 aria-hidden="true" className="size-3" />
                </button>
              </div>
            </div>
          </div>
        ))}
        {images.length === 0 ? <span className="text-xs">아직 이미지가 없습니다.</span> : null}
      </div>
    </div>
  )
}
