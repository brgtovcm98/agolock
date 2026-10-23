import { useMutation, useQueries, useQuery, useQueryClient } from '@tanstack/react-query'
import { ImagePlus, Star, Trash2, X } from 'lucide-react'
import { useRef, useState } from 'react'
import { ApiError } from '@/shared/api/client'
import { Alert } from '@/shared/ui/alert'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { mediaApi, type ImageStatus, type ImageTargetType } from './api'

const statusLabel: Record<ImageStatus, string> = {
  UPLOADING: '업로드 중',
  DRAFT: '임시',
  PENDING: '처리 대기',
  PROCESSING: '처리 중',
  READY: '준비됨',
  FAILED: '실패',
  DELETED: '삭제됨',
}

const MAX_IMAGE_BYTES = 10 * 1024 * 1024
const MAX_EDGE = 2048

async function toWebp(file: File): Promise<File> {
  if (file.type === 'image/webp' && file.size <= MAX_IMAGE_BYTES) return file

  let bitmap: ImageBitmap
  try {
    bitmap = await createImageBitmap(file)
  } catch {
    throw new Error('이미지를 읽지 못했습니다. JPG·PNG·WebP로 변환해 주세요.')
  }

  const scale = Math.min(1, MAX_EDGE / Math.max(bitmap.width, bitmap.height))
  const canvas = document.createElement('canvas')
  canvas.width = Math.max(1, Math.round(bitmap.width * scale))
  canvas.height = Math.max(1, Math.round(bitmap.height * scale))
  const context = canvas.getContext('2d')
  if (!context) throw new Error('이미지를 변환하지 못했습니다.')
  context.drawImage(bitmap, 0, 0, canvas.width, canvas.height)
  bitmap.close()

  const blob = await new Promise<Blob | null>((resolve) =>
    canvas.toBlob(resolve, 'image/webp', 0.8),
  )
  if (!blob) throw new Error('WebP 변환에 실패했습니다.')
  if (blob.size > MAX_IMAGE_BYTES) throw new Error('이미지는 10MB 이하여야 합니다.')

  return new File([blob], file.name.replace(/\.[^.]+$/, '') + '.webp', {
    type: 'image/webp',
  })
}

export function ImageGallery({
  targetType,
  targetId,
  canUpload = true,
  relatedQueryKey,
}: {
  targetType: ImageTargetType
  targetId: string
  canUpload?: boolean
  relatedQueryKey?: readonly unknown[]
}) {
  const queryClient = useQueryClient()
  const inputRef = useRef<HTMLInputElement>(null)
  const abortRef = useRef<XMLHttpRequest | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [lastFile, setLastFile] = useState<File | null>(null)
  const [uploadProgress, setUploadProgress] = useState(0)

  const queryKey = ['images', targetType.toLowerCase(), targetId] as const
  const imagesQuery = useQuery({
    queryKey,
    queryFn: () => mediaApi.images(targetType, targetId),
    refetchInterval: (query) => {
      const items = query.state.data?.items ?? []
      return items.some((image) => ['UPLOADING', 'PENDING', 'PROCESSING'].includes(image.status))
        ? 2000
        : false
    },
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
  const singleImage = images.length === 1

  const refresh = async () => {
    await queryClient.invalidateQueries({ queryKey })
    if (relatedQueryKey) {
      await queryClient.invalidateQueries({ queryKey: relatedQueryKey })
    }
  }
  const fail = (cause: unknown) =>
    setError(
      cause instanceof DOMException && cause.name === 'AbortError'
        ? '업로드를 취소했습니다.'
        : cause instanceof ApiError
          ? cause.message
          : cause instanceof Error
            ? cause.message
            : '이미지 요청을 처리하지 못했습니다.',
    )

  const uploadMutation = useMutation({
    mutationFn: async (file: File) => {
      setLastFile(file)
      setUploadProgress(0)
      const webp = await toWebp(file)
      const presigned = await mediaApi.presign(targetType, targetId, webp.size)
      await new Promise<void>((resolve, reject) => {
        const xhr = new XMLHttpRequest()
        xhr.open('PUT', presigned.uploadUrl)
        xhr.setRequestHeader('Content-Type', 'image/webp')
        xhr.upload.addEventListener('progress', (event) => {
          if (event.lengthComputable) {
            setUploadProgress(Math.round((event.loaded / event.total) * 100))
          }
        })
        xhr.addEventListener('load', () => {
          if (xhr.status >= 200 && xhr.status < 300) resolve()
          else reject(new Error('업로드에 실패했습니다.'))
        })
        xhr.addEventListener('error', () => reject(new Error('업로드에 실패했습니다.')))
        xhr.addEventListener('abort', () => reject(new DOMException('Aborted', 'AbortError')))
        abortRef.current = xhr
        xhr.send(webp)
      })
      abortRef.current = null
      setUploadProgress(100)
      await mediaApi.complete(presigned.imageId, presigned.objectKey)
    },
    onSuccess: async () => {
      setError(null)
      setLastFile(null)
      setUploadProgress(0)
      await refresh()
    },
    onError: (cause) => {
      abortRef.current = null
      setUploadProgress(0)
      fail(cause)
    },
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
        <span className="text-xs font-black">이미지 {images.length}/10</span>
        <div className="flex items-center gap-2">
          {uploadMutation.isPending ? (
            <Button
              type="button"
              size="sm"
              variant="secondary"
              onClick={() => abortRef.current?.abort()}
            >
              <X aria-hidden="true" className="size-4" />
              취소
            </Button>
          ) : null}
          {canUpload ? (
            <>
              <input
                ref={inputRef}
                type="file"
                accept="image/*"
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
                disabled={uploadMutation.isPending || images.length >= 10}
                onClick={() => inputRef.current?.click()}
              >
                <ImagePlus aria-hidden="true" className="size-4" />
                사진 추가
              </Button>
            </>
          ) : null}
        </div>
      </div>
      {uploadMutation.isPending ? (
        <div className="flex items-center gap-2">
          <div
            role="progressbar"
            aria-label="이미지 업로드 진행률"
            aria-valuemin={0}
            aria-valuemax={100}
            aria-valuenow={uploadProgress}
            className="border-border bg-sunken h-3 min-w-0 flex-1 border-2"
          >
            <div
              className="bg-primary h-full transition-[width] duration-150"
              style={{ width: `${uploadProgress}%` }}
            />
          </div>
          <span className="w-9 text-right text-[11px] font-black">{uploadProgress}%</span>
        </div>
      ) : null}
      {error ? (
        <div className="flex flex-col gap-2">
          <Alert variant="error">{error}</Alert>
          {lastFile && canUpload && !uploadMutation.isPending ? (
            <Button
              type="button"
              size="sm"
              variant="secondary"
              onClick={() => uploadMutation.mutate(lastFile)}
            >
              다시 시도
            </Button>
          ) : null}
        </div>
      ) : null}
      <div className="flex flex-wrap gap-2">
        {images.map((image) => (
          <div
            key={image.id}
            className={
              singleImage
                ? 'border-border flex w-full max-w-lg flex-col gap-1 border-2 p-1'
                : 'border-border flex w-36 flex-col gap-1 border-2 p-1 lg:w-44'
            }
          >
            {image.status === 'READY' && urlMap.get(image.id) ? (
              <img
                src={urlMap.get(image.id)}
                alt={`${targetType === 'ITEM' ? '물건' : '재고'} 이미지`}
                className={
                  singleImage
                    ? 'aspect-[4/3] w-full object-cover'
                    : 'aspect-square w-full object-cover'
                }
              />
            ) : (
              <div
                className={
                  singleImage
                    ? 'bg-sunken flex aspect-[4/3] items-center justify-center text-[10px]'
                    : 'bg-sunken flex aspect-square items-center justify-center text-[10px]'
                }
              >
                {statusLabel[image.status]}
              </div>
            )}
            <div className="flex items-center justify-between">
              <Badge variant={image.status === 'FAILED' ? 'dis' : 'neutral'}>
                {statusLabel[image.status]}
              </Badge>
              {canUpload ? (
                <div className="flex">
                  <button
                    type="button"
                    aria-label="대표 이미지"
                    disabled={image.status !== 'READY' || image.isPrimary}
                    onClick={() => primaryMutation.mutate(image.id)}
                    className="hover:bg-sunken hover:border-border grid size-8 place-items-center border border-transparent disabled:opacity-40"
                  >
                    <Star aria-hidden="true" className="size-3.5" />
                  </button>
                  <button
                    type="button"
                    aria-label="이미지 삭제"
                    onClick={() => deleteMutation.mutate(image.id)}
                    className="hover:bg-sunken hover:border-border grid size-8 place-items-center border border-transparent"
                  >
                    <Trash2 aria-hidden="true" className="size-3.5" />
                  </button>
                </div>
              ) : null}
            </div>
          </div>
        ))}
        {images.length === 0 ? <span className="text-xs">아직 이미지가 없습니다.</span> : null}
      </div>
    </div>
  )
}
