import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Plus, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { inventoryApi, type Category } from '@/features/inventory/api'
import { useAuth } from '@/features/auth/auth-provider'
import { ApiError } from '@/shared/api/client'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { ConfirmDialog } from '@/shared/ui/confirm-dialog'
import { MetaText, PageTitle, SectionTitle } from '@/shared/ui/typography'
import { TextField } from '@/shared/ui/text-field'

export function CategorySettingsPage() {
  const queryClient = useQueryClient()
  const { user, space } = useAuth()
  const owner = Boolean(user && space && user.id === space.ownerUserId)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null)

  const categoriesQuery = useQuery({
    queryKey: ['category', 'list'],
    queryFn: inventoryApi.categories,
  })
  const categories = categoriesQuery.data?.items ?? []

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['category', 'list'] }),
      queryClient.invalidateQueries({ queryKey: ['inventory', 'categories'] }),
      queryClient.invalidateQueries({ queryKey: ['item'] }),
    ])
  }

  const createMutation = useMutation({
    mutationFn: () => inventoryApi.createCategory(name.trim()),
    onSuccess: async () => {
      setName('')
      setError(null)
      await invalidate()
    },
    onError: (cause) =>
      setError(cause instanceof ApiError ? cause.message : '카테고리를 만들지 못했습니다.'),
  })

  const deleteMutation = useMutation({
    mutationFn: (categoryId: string) => inventoryApi.deleteCategory(categoryId),
    onSuccess: async () => {
      setDeleteTarget(null)
      setError(null)
      await invalidate()
    },
    onError: (cause) => {
      setError(
        cause instanceof ApiError
          ? cause.code === 'CATEGORY_IN_USE'
            ? '물건이 사용 중인 카테고리는 삭제할 수 없어요. 다른 카테고리로 옮긴 뒤 삭제해 주세요.'
            : cause.message
          : '카테고리를 삭제하지 못했습니다.',
      )
      setDeleteTarget(null)
    },
  })

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-1">
        <Link to="/settings" className="text-xs font-bold underline underline-offset-2">
          ← 설정
        </Link>
        <p className="label-caps text-foreground/60">설정</p>
        <PageTitle>카테고리 관리</PageTitle>
        <MetaText>물건을 묶어 볼 때 사용할 카테고리를 관리합니다.</MetaText>
      </div>

      {!owner ? <Alert variant="info">카테고리는 Space 소유자만 관리할 수 있어요.</Alert> : null}
      {error ? <Alert variant="error">{error}</Alert> : null}

      <Card>
        <CardBody className="gap-3">
          <SectionTitle aside={<span>{categories.length}개</span>}>카테고리</SectionTitle>
          {categoriesQuery.isLoading ? (
            <p role="status" className="text-xs">
              불러오는 중…
            </p>
          ) : categoriesQuery.isError ? (
            <Alert variant="error">카테고리를 불러오지 못했습니다.</Alert>
          ) : categories.length === 0 ? (
            <p className="text-foreground/70 text-xs">아직 카테고리가 없습니다.</p>
          ) : (
            <ul className="flex flex-col gap-2">
              {categories.map((category) => (
                <li
                  key={category.id}
                  className="border-border bg-card flex items-center justify-between gap-2 border-2 px-3 py-2"
                >
                  <span className="text-sm font-black">{category.name}</span>
                  {owner ? (
                    <Button
                      type="button"
                      size="sm"
                      variant="secondary"
                      className="border-danger text-danger"
                      aria-label={`${category.name} 삭제`}
                      onClick={() => setDeleteTarget(category)}
                    >
                      <Trash2 aria-hidden="true" className="size-4" />
                      삭제
                    </Button>
                  ) : null}
                </li>
              ))}
            </ul>
          )}

          {owner ? (
            <form
              className="flex items-end gap-2"
              onSubmit={(event) => {
                event.preventDefault()
                if (name.trim()) createMutation.mutate()
              }}
            >
              <TextField
                label="새 카테고리"
                value={name}
                maxLength={80}
                onChange={(event) => setName(event.target.value)}
              />
              <Button
                type="submit"
                disabled={!name.trim() || createMutation.isPending}
                className="mb-0.5"
              >
                <Plus aria-hidden="true" className="size-4" />
                {createMutation.isPending ? '만드는 중…' : '추가'}
              </Button>
            </form>
          ) : null}
        </CardBody>
      </Card>

      <ConfirmDialog
        open={deleteTarget !== null}
        onOpenChange={(next) => !next && setDeleteTarget(null)}
        title="카테고리를 삭제할까요?"
        description={
          deleteTarget
            ? `“${deleteTarget.name}” 카테고리를 삭제합니다. 사용 중인 물건이 있으면 삭제할 수 없어요.`
            : ''
        }
        confirmLabel="삭제"
        pending={deleteMutation.isPending}
        destructive
        onConfirm={() => deleteTarget && deleteMutation.mutate(deleteTarget.id)}
      />
    </div>
  )
}
