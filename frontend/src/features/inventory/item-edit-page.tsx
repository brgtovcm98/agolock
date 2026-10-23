import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { inventoryApi, type Category, type Item, type ItemUpdateImpact } from './api'
import { ApiError } from '@/shared/api/client'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { ConfirmDialog } from '@/shared/ui/confirm-dialog'
import { MetaText, PageTitle, SectionTitle } from '@/shared/ui/typography'
import { TextField } from '@/shared/ui/text-field'
import { useToast } from '@/shared/ui/toast-context'

function ItemEditForm({ item, categories }: { item: Item; categories: Category[] }) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { show } = useToast()
  const [name, setName] = useState(item.name)
  const [description, setDescription] = useState(item.description ?? '')
  const [categoryId, setCategoryId] = useState(item.categoryId ?? '')
  const [price, setPrice] = useState(item.priceAmount !== null ? String(item.priceAmount) : '')
  const [error, setError] = useState<string | null>(null)
  const [impact, setImpact] = useState<ItemUpdateImpact | null>(null)

  const updateMutation = useMutation({
    mutationFn: async () => {
      const priceAmount = price.trim() ? Number(price) : null
      if (!name.trim()) throw new Error('물건 이름을 입력해 주세요.')
      if (priceAmount !== null && (!Number.isInteger(priceAmount) || priceAmount < 0)) {
        throw new Error('가격은 0원 이상의 정수로 입력해 주세요.')
      }
      return inventoryApi.updateItem(item.id, {
        version: item.version,
        name: name.trim(),
        description: description.trim() || null,
        categoryId: categoryId || null,
        priceAmount,
      })
    },
    onSuccess: async () => {
      show({
        id: `item:${item.id}:save`,
        title: '물건 정보를 저장했어요.',
        description: item.name,
        variant: 'success',
      })
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['item'] }),
        queryClient.invalidateQueries({ queryKey: ['inventory'] }),
        queryClient.invalidateQueries({ queryKey: ['hierarchy'] }),
      ])
      navigate(`/items/${item.id}`, { replace: true })
    },
    onError: (cause) => {
      setImpact(null)
      if (cause instanceof ApiError && cause.status === 409) {
        setError('다른 곳에서 먼저 수정했어요. 최신 정보를 불러온 뒤 다시 저장해 주세요.')
        return
      }
      setError(
        cause instanceof ApiError
          ? cause.message
          : cause instanceof Error
            ? cause.message
            : '저장하지 못했습니다.',
      )
    },
  })

  const checkImpactMutation = useMutation({
    mutationFn: () => inventoryApi.itemUpdateImpact(item.id),
    onSuccess: (result) => {
      if (result.affectedInventoryCount === 0 && result.protectedByOverrideCount === 0) {
        updateMutation.mutate()
        return
      }
      setImpact(result)
    },
    onError: (cause) =>
      setError(cause instanceof ApiError ? cause.message : '영향 범위를 확인하지 못했습니다.'),
  })

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <div className="flex flex-col gap-1">
        <Link to={`/items/${item.id}`} className="text-xs font-bold underline underline-offset-2">
          ← 물건 상세
        </Link>
        <p className="label-caps text-foreground/60">물건 수정</p>
        <PageTitle>{item.name}</PageTitle>
        <MetaText>마스터 값을 바꾸면 재고값을 덮어쓰지 않은 재고에 바로 반영됩니다.</MetaText>
      </div>

      {error ? <Alert variant="error">{error}</Alert> : null}

      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault()
          setError(null)
          checkImpactMutation.mutate()
        }}
      >
        <Card>
          <CardBody className="gap-4">
            <SectionTitle>물건 정보</SectionTitle>
            <TextField
              label="이름"
              value={name}
              maxLength={120}
              onChange={(event) => setName(event.target.value)}
            />
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-black">설명 (선택)</span>
              <textarea
                value={description}
                maxLength={2000}
                onChange={(event) => setDescription(event.target.value)}
                rows={3}
                className="border-border bg-card focus-visible:ring-foreground focus-visible:ring-offset-background min-h-20 w-full border-[2.5px] px-3 py-2 text-sm font-medium outline-none focus-visible:ring-2 focus-visible:ring-offset-2"
              />
            </label>
            <label className="flex flex-col gap-1.5">
              <span className="text-xs font-black">카테고리 (선택)</span>
              <select
                aria-label="카테고리"
                value={categoryId}
                onChange={(event) => setCategoryId(event.target.value)}
                className="border-border bg-card h-12 border-[2.5px] px-3 text-sm"
              >
                <option value="">없음</option>
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </label>
            <TextField
              label="가격 (선택)"
              type="number"
              min={0}
              inputMode="numeric"
              value={price}
              onChange={(event) => setPrice(event.target.value)}
            />
          </CardBody>
        </Card>

        <div className="flex justify-end gap-2">
          <Button asChild variant="secondary">
            <Link to={`/items/${item.id}`}>취소</Link>
          </Button>
          <Button
            type="submit"
            disabled={checkImpactMutation.isPending || updateMutation.isPending}
          >
            {checkImpactMutation.isPending || updateMutation.isPending ? '확인 중…' : '저장'}
          </Button>
        </div>
      </form>

      <ConfirmDialog
        open={impact !== null}
        onOpenChange={(next) => !next && setImpact(null)}
        title="재고에 반영할까요?"
        description={
          impact
            ? `이 물건을 사용하는 재고 ${impact.affectedInventoryCount}개에 바뀐 값이 반영됩니다. 재고값을 직접 지정한 ${impact.protectedByOverrideCount}개는 그대로 유지됩니다.`
            : ''
        }
        confirmLabel="반영하고 저장"
        pending={updateMutation.isPending}
        onConfirm={() => updateMutation.mutate()}
      />
    </div>
  )
}

export function ItemEditPage() {
  const { itemId = '' } = useParams()
  const itemQuery = useQuery({
    queryKey: ['item', 'detail', itemId],
    queryFn: () => inventoryApi.item(itemId),
    enabled: Boolean(itemId),
  })
  const categoriesQuery = useQuery({
    queryKey: ['category', 'list'],
    queryFn: inventoryApi.categories,
  })

  if (itemQuery.isLoading) {
    return (
      <p role="status" className="text-sm font-bold">
        물건을 불러오는 중…
      </p>
    )
  }
  if (itemQuery.isError || !itemQuery.data) {
    return <Alert variant="error">물건을 찾을 수 없습니다.</Alert>
  }
  if (itemQuery.data.effectivePermission !== 'OWNER') {
    return <Alert variant="error">물건 마스터는 소유자만 수정할 수 있습니다.</Alert>
  }

  return (
    <ItemEditForm
      key={`${itemQuery.data.id}-${itemQuery.data.version}`}
      item={itemQuery.data}
      categories={categoriesQuery.data?.items ?? []}
    />
  )
}
