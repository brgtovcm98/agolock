import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Plus, X } from 'lucide-react'
import { useState } from 'react'
import { inventoryApi, type Category } from '../api'
import { ApiError } from '@/shared/api/client'
import { Alert } from '@/shared/ui/alert'
import { Select } from '@/shared/ui/select'
import { Button } from '@/shared/ui/button'

export function CategoryCombobox({
  value,
  categories,
  canCreate,
  onChange,
}: {
  value: string
  categories: Category[]
  canCreate: boolean
  onChange: (categoryId: string) => void
}) {
  const queryClient = useQueryClient()
  const [creating, setCreating] = useState(false)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)

  const createMutation = useMutation({
    mutationFn: () => inventoryApi.createCategory(name.trim()),
    onSuccess: async (category) => {
      setName('')
      setCreating(false)
      setError(null)
      onChange(category.id)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['category', 'list'] }),
        queryClient.invalidateQueries({ queryKey: ['inventory', 'categories'] }),
      ])
    },
    onError: (cause) =>
      setError(cause instanceof ApiError ? cause.message : '카테고리를 만들지 못했습니다.'),
  })

  return (
    <div className="flex flex-col gap-2">
      <div className="flex items-end gap-2">
        <label className="flex min-w-0 flex-1 flex-col gap-2">
          <span className="text-xs font-black">카테고리 (선택)</span>
          <Select
            aria-label="카테고리"
            value={value}
            onChange={(event) => onChange(event.target.value)}
          >
            <option value="">없음</option>
            {categories.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </Select>
        </label>
        {canCreate ? (
          <Button
            type="button"
            size="sm"
            variant="secondary"

            onClick={() => {
              setCreating((current) => !current)
              setError(null)
            }}
            aria-expanded={creating}
          >
            {creating ? (
              <X aria-hidden="true" className="size-4" />
            ) : (
              <Plus aria-hidden="true" className="size-4" />
            )}
            새 카테고리
          </Button>
        ) : null}
      </div>

      {creating ? (
        <div className="border-border bg-sunken flex flex-col gap-2 border-2 p-2">
          <label className="flex flex-col gap-2">
            <span className="text-label font-black">새 카테고리 이름</span>
            <input
              value={name}
              maxLength={80}
              onChange={(event) => setName(event.target.value)}
              className="border-border bg-card focus-visible:ring-foreground focus-visible:ring-offset-background h-10 border-2 px-2 text-sm font-medium outline-none focus-visible:ring-2 focus-visible:ring-offset-2"
              placeholder="예: 공구"
            />
          </label>
          <div className="flex justify-end">
            <Button
              type="button"
              size="sm"
              disabled={!name.trim() || createMutation.isPending}
              onClick={() => createMutation.mutate()}
            >
              {createMutation.isPending ? '만드는 중…' : '만들고 선택'}
            </Button>
          </div>
        </div>
      ) : null}

      {error ? <Alert variant="error">{error}</Alert> : null}
    </div>
  )
}
