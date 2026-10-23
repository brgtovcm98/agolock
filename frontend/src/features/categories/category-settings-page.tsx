import * as Dialog from '@radix-ui/react-dialog'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { GitMerge, Pencil, Plus, Trash2, X } from 'lucide-react'
import { useState } from 'react'
import { inventoryApi, type Category, type CategoryMergeResult } from '@/features/inventory/api'
import { useAuth } from '@/features/auth/auth-provider'
import { ApiError } from '@/shared/api/client'
import { Alert } from '@/shared/ui/alert'
import { Select } from '@/shared/ui/select'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { ConfirmDialog } from '@/shared/ui/confirm-dialog'
import { PageHeader } from '@/shared/ui/page-header'
import { SectionTitle } from '@/shared/ui/typography'
import { TextField } from '@/shared/ui/text-field'
import { useToast } from '@/shared/ui/toast-context'

function DialogFrame({
  open,
  onOpenChange,
  title,
  children,
}: {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  children: React.ReactNode
}) {
  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Portal>
        <Dialog.Overlay className="dialog-overlay bg-foreground/40 fixed inset-0 z-50" />
        <Dialog.Content className="dialog-content border-border bg-background shadow-brutal fixed top-1/2 left-1/2 z-50 flex w-[min(92vw,420px)] -translate-x-1/2 -translate-y-1/2 flex-col gap-4 border-[2.5px] p-4">
          <div className="flex items-start justify-between gap-3">
            <Dialog.Title className="text-xl font-black">{title}</Dialog.Title>
            <Dialog.Close asChild>
              <button
                type="button"
                aria-label="닫기"
                className="border-border bg-card grid size-11 shrink-0 place-items-center border-[2.5px]"
              >
                <X aria-hidden="true" className="size-4" />
              </button>
            </Dialog.Close>
          </div>
          {children}
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

function CategoryEditDialog({
  category,
  onClose,
  onSaved,
}: {
  category: Category
  onClose: () => void
  onSaved: () => Promise<void>
}) {
  const [name, setName] = useState(category.name)
  const [error, setError] = useState<string | null>(null)
  const { show } = useToast()
  const mutation = useMutation({
    mutationFn: () => inventoryApi.updateCategory(category.id, { name: name.trim() }),
    onSuccess: async (updated) => {
      show({
        id: `category:${category.id}:rename`,
        title: '카테고리 이름을 바꿨어요.',
        description: updated.name,
        variant: 'success',
      })
      await onSaved()
      onClose()
    },
    onError: (cause) =>
      setError(cause instanceof ApiError ? cause.message : '저장하지 못했습니다.'),
  })

  return (
    <DialogFrame open onOpenChange={(next) => !next && onClose()} title="카테고리 이름 변경">
      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault()
          if (name.trim()) mutation.mutate()
        }}
      >
        {error ? <Alert variant="error">{error}</Alert> : null}
        <TextField
          label="카테고리 이름"
          value={name}
          maxLength={80}
          onChange={(event) => setName(event.target.value)}
          autoFocus
        />
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" disabled={!name.trim() || mutation.isPending}>
            {mutation.isPending ? '저장 중…' : '저장'}
          </Button>
        </div>
      </form>
    </DialogFrame>
  )
}

function CategoryMergeDialog({
  source,
  categories,
  onClose,
  onMerged,
}: {
  source: Category
  categories: Category[]
  onClose: () => void
  onMerged: (result: CategoryMergeResult) => Promise<void>
}) {
  const targets = categories.filter((category) => category.id !== source.id)
  const [targetId, setTargetId] = useState(targets[0]?.id ?? '')
  const [error, setError] = useState<string | null>(null)
  const mutation = useMutation({
    mutationFn: () => inventoryApi.mergeCategory(source.id, targetId),
    onSuccess: async (result) => {
      await onMerged(result)
      onClose()
    },
    onError: (cause) =>
      setError(cause instanceof ApiError ? cause.message : '병합하지 못했습니다.'),
  })

  return (
    <DialogFrame open onOpenChange={(next) => !next && onClose()} title={`“${source.name}” 병합`}>
      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault()
          if (targetId) mutation.mutate()
        }}
      >
        {error ? <Alert variant="error">{error}</Alert> : null}
        <p className="text-foreground/70 text-xs">
          이 카테고리를 사용하는 물건과 재고값을 선택한 카테고리로 옮기고, 현재 카테고리는
          삭제합니다.
        </p>
        <label className="flex flex-col gap-2">
          <span className="text-xs font-black">옮길 카테고리</span>
          <Select
            aria-label="옮길 카테고리"
            value={targetId}
            onChange={(event) => setTargetId(event.target.value)}
          >
            {targets.map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </Select>
        </label>
        <div className="flex justify-end gap-2">
          <Button type="button" variant="secondary" onClick={onClose}>
            취소
          </Button>
          <Button type="submit" disabled={!targetId || mutation.isPending}>
            {mutation.isPending ? '병합 중…' : '병합'}
          </Button>
        </div>
      </form>
    </DialogFrame>
  )
}

export function CategorySettingsPage() {
  const queryClient = useQueryClient()
  const { show } = useToast()
  const { user, space } = useAuth()
  const owner = Boolean(user && space && user.id === space.ownerUserId)
  const [name, setName] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [deleteTarget, setDeleteTarget] = useState<Category | null>(null)
  const [editTarget, setEditTarget] = useState<Category | null>(null)
  const [mergeTarget, setMergeTarget] = useState<Category | null>(null)
  const [mergeResult, setMergeResult] = useState<CategoryMergeResult | null>(null)

  const categoriesQuery = useQuery({
    queryKey: ['category', 'list'],
    queryFn: inventoryApi.categories,
  })
  const categories = categoriesQuery.data?.items ?? []

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['category', 'list'] }),
      queryClient.invalidateQueries({ queryKey: ['inventory', 'categories'] }),
      queryClient.invalidateQueries({ queryKey: ['inventory'] }),
      queryClient.invalidateQueries({ queryKey: ['item'] }),
    ])
  }

  const createMutation = useMutation({
    mutationFn: () => inventoryApi.createCategory(name.trim()),
    onSuccess: async (created) => {
      show({
        id: `category:${created.id}:create`,
        title: '카테고리를 만들었어요.',
        description: created.name,
        variant: 'success',
      })
      setName('')
      setError(null)
      await invalidate()
    },
    onError: (cause) =>
      setError(cause instanceof ApiError ? cause.message : '카테고리를 만들지 못했습니다.'),
  })

  const deleteMutation = useMutation({
    mutationFn: (categoryId: string) => inventoryApi.deleteCategory(categoryId),
    onSuccess: async (_data, categoryId) => {
      show({
        id: `category:${categoryId}:delete`,
        title: '카테고리를 삭제했어요.',
        description: categories.find((category) => category.id === categoryId)?.name,
        variant: 'success',
      })
      setDeleteTarget(null)
      setError(null)
      await invalidate()
    },
    onError: (cause) => {
      setError(
        cause instanceof ApiError
          ? cause.code === 'CATEGORY_IN_USE'
            ? '물건이 사용 중인 카테고리는 삭제할 수 없어요. 다른 카테고리로 병합한 뒤 삭제해 주세요.'
            : cause.message
          : '카테고리를 삭제하지 못했습니다.',
      )
      setDeleteTarget(null)
    },
  })

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <PageHeader
        back={{ to: '/settings', label: '설정' }}
        title="카테고리 관리"
        description="물건을 묶어 볼 때 사용할 카테고리를 관리합니다."
      />

      {!owner ? <Alert variant="info">카테고리는 공간 소유자만 관리할 수 있어요.</Alert> : null}
      {error ? <Alert variant="error">{error}</Alert> : null}
      {mergeResult ? (
        <Alert variant="success">
          카테고리를 병합했어요. 물건 {mergeResult.mergedItemCount}개, 재고{' '}
          {mergeResult.mergedInventoryCount}개를 옮겼습니다.
        </Alert>
      ) : null}

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
                  className="border-border bg-card flex flex-wrap items-center justify-between gap-2 border-2 px-3 py-2"
                >
                  <span className="text-sm font-black">{category.name}</span>
                  {owner ? (
                    <div className="flex flex-wrap gap-2">
                      <Button
                        type="button"
                        size="sm"
                        variant="secondary"
                        className="w-11 px-0 sm:w-auto sm:px-3"
                        aria-label={`${category.name} 이름 변경`}
                        onClick={() => {
                          setMergeResult(null)
                          setEditTarget(category)
                        }}
                      >
                        <Pencil aria-hidden="true" className="size-4" />
                        <span className="hidden sm:inline">이름 변경</span>
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant="secondary"
                        className="w-11 px-0 sm:w-auto sm:px-3"
                        aria-label={`${category.name} 병합`}
                        disabled={categories.length < 2}
                        onClick={() => {
                          setMergeResult(null)
                          setMergeTarget(category)
                        }}
                      >
                        <GitMerge aria-hidden="true" className="size-4" />
                        <span className="hidden sm:inline">병합</span>
                      </Button>
                      <Button
                        type="button"
                        size="sm"
                        variant="secondary"
                        className="border-danger text-danger w-11 px-0 sm:w-auto sm:px-3"
                        aria-label={`${category.name} 삭제`}
                        onClick={() => setDeleteTarget(category)}
                      >
                        <Trash2 aria-hidden="true" className="size-4" />
                        <span className="hidden sm:inline">삭제</span>
                      </Button>
                    </div>
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
              <div className="min-w-0 flex-1">
                <TextField
                  label="새 카테고리"
                  value={name}
                  maxLength={80}
                  onChange={(event) => setName(event.target.value)}
                />
              </div>
              <Button type="submit" disabled={!name.trim() || createMutation.isPending}>
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

      {editTarget ? (
        <CategoryEditDialog
          key={editTarget.id}
          category={editTarget}
          onClose={() => setEditTarget(null)}
          onSaved={invalidate}
        />
      ) : null}

      {mergeTarget ? (
        <CategoryMergeDialog
          key={mergeTarget.id}
          source={mergeTarget}
          categories={categories}
          onClose={() => setMergeTarget(null)}
          onMerged={async (result) => {
            setMergeResult(result)
            setError(null)
            await invalidate()
          }}
        />
      ) : null}
    </div>
  )
}
