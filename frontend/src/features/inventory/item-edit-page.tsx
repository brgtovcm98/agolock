import { collectPages } from '@/features/inventory/api'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  inventoryApi,
  type Category,
  type Item,
  type ItemUpdateImpact,
  type UpdateItemInput,
} from './api'
import { sparsePatch, parsePrice, invalidateInventory } from './edit-state'
import { DirtyLeave } from './dirty-leave'
import { useEditConflict } from './use-edit-conflict'
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

const impactFieldLabels: Record<string, string> = {
  name: '이름',
  description: '설명',
  categoryId: '카테고리',
  priceAmount: '가격',
}

function ItemEditForm({ item, categories }: { item: Item; categories: Category[] }) {
  const navigate = useNavigate()
  const [saved, setSaved] = useState(false)
  const queryClient = useQueryClient()
  const { show } = useToast()
  const [name, setName] = useState(item.name)
  const [description, setDescription] = useState(item.description ?? '')
  const [categoryId, setCategoryId] = useState(item.categoryId ?? '')
  const [price, setPrice] = useState(item.priceAmount != null ? String(item.priceAmount) : '')
  const [error, setError] = useState<string | null>(null)
  const [impact, setImpact] = useState<ItemUpdateImpact | null>(null)
  const edit = useEditConflict(
    {
      name: item.name,
      description: item.description ?? null,
      categoryId: item.categoryId ?? null,
      priceAmount: item.priceAmount ?? null,
    },
    item.version,
  )
  const fields = {
    name: name.trim(),
    description:
      description === (edit.baseline.description ?? '') ? edit.baseline.description : description,
    categoryId: categoryId || null,
    priceAmount: price.trim() ? Number(price) : null,
  }
  const patch = sparsePatch(edit.baseline, fields)
  const dirty = Object.keys(patch).length > 0
  const [frozen, setFrozen] = useState<UpdateItemInput | null>(null)
  function apply(next: typeof fields) {
    setName(next.name)
    setDescription(next.description ?? '')
    setCategoryId(next.categoryId ?? '')
    setPrice(next.priceAmount === null ? '' : String(next.priceAmount))
  }

  const updateMutation = useMutation({
    mutationFn: async (request: UpdateItemInput) => {
      if (!request) throw new Error('변경 내용을 다시 확인해 주세요.')
      return inventoryApi.updateItem(item.id, request)
    },
    onSuccess: async () => {
      setSaved(true)
      show({
        id: `item:${item.id}:save`,
        title: '물건 정보를 저장했어요.',
        description: item.name,
        variant: 'success',
      })
      await invalidateInventory(queryClient)
      navigate(`/items/${item.id}`, { replace: true })
    },
    onError: async (cause) => {
      setImpact(null)
      if (
        await edit.recover(
          cause,
          fields,
          async () => {
            const latest = await inventoryApi.item(item.id)
            void queryClient.invalidateQueries({ queryKey: ['category'] })
            return {
              fields: {
                name: latest.name,
                description: latest.description ?? null,
                categoryId: latest.categoryId ?? null,
                priceAmount: latest.priceAmount ?? null,
              },
              version: latest.version,
            }
          },
          apply,
        )
      ) {
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
    mutationFn: async () => {
      parsePrice(price)
      if (!name.trim()) throw new Error('물건 이름을 입력해 주세요.')
      if (categoryId && !categories.some((category) => category.id === categoryId))
        throw new Error('카테고리를 다시 선택해 주세요.')
      const request = { version: edit.version, ...patch }
      setFrozen(request)
      return { impact: await inventoryApi.itemUpdateImpact(item.id, request), request }
    },
    onSuccess: ({ impact: result, request }) => {
      if (result.totalInventoryCount === 0) {
        updateMutation.mutate(request)
        return
      }
      setImpact(result)
    },
    onError: async (cause) => {
      await edit.recover(
        cause,
        fields,
        async () => {
          const latest = await inventoryApi.item(item.id)
          void queryClient.invalidateQueries({ queryKey: ['category'] })
          return {
            fields: {
              name: latest.name,
              description: latest.description ?? null,
              categoryId: latest.categoryId ?? null,
              priceAmount: latest.priceAmount ?? null,
            },
            version: latest.version,
          }
        },
        apply,
      )
      setError(cause instanceof Error ? cause.message : '영향 범위를 확인하지 못했습니다.')
    },
  })

  return (
    <div className="flex max-w-2xl flex-col gap-6">
      <DirtyLeave dirty={dirty && !saved} />
      <PageHeader
        back={{ to: `/items/${item.id}`, label: '물건 상세' }}
        eyebrow="물건 수정"
        title={item.name}
        description="마스터 값을 바꾸면 재고값을 덮어쓰지 않은 재고에 바로 반영됩니다."
      />

      {error ? <Alert variant="error">{error}</Alert> : null}
      {edit.blocked ? (
        <Alert variant="error">
          최신 정보를 확인할 수 없어 저장을 중단했어요. 입력은 유지됩니다.
        </Alert>
      ) : null}
      {edit.choices(
        fields,
        (key, value) => apply({ ...fields, [key]: value }),
        (key, value) =>
          String(key).startsWith('categoryId')
            ? (categories.find((category) => category.id === value)?.name ?? String(value))
            : typeof value === 'number'
              ? `${value.toLocaleString('ko-KR')}원`
              : String(value),
      )}

      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault()
          setError(null)
          checkImpactMutation.mutate()
        }}
      >
        <fieldset
          disabled={updateMutation.isPending || checkImpactMutation.isPending}
          className="flex flex-col gap-4"
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
              <label className="flex flex-col gap-2">
                <span className="text-xs font-black">설명 (선택)</span>
                <textarea
                  value={description}
                  maxLength={2000}
                  onChange={(event) => setDescription(event.target.value)}
                  rows={3}
                  className="border-border bg-card focus-visible:ring-foreground focus-visible:ring-offset-background min-h-20 w-full border-[2.5px] px-3 py-2 text-sm font-medium outline-none focus-visible:ring-2 focus-visible:ring-offset-2"
                />
              </label>
              <label className="flex flex-col gap-2">
                <span className="text-xs font-black">카테고리 (선택)</span>
                <Select
                  aria-label="카테고리"
                  value={categoryId}
                  onChange={(event) => setCategoryId(event.target.value)}
                >
                  <option value="">없음</option>
                  {categories.map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
                </Select>
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
              disabled={
                !dirty ||
                edit.blocked ||
                edit.unresolved ||
                checkImpactMutation.isPending ||
                updateMutation.isPending
              }
            >
              {checkImpactMutation.isPending || updateMutation.isPending ? '확인 중…' : '저장'}
            </Button>
          </div>
        </fieldset>
      </form>

      <ConfirmDialog
        open={impact != null}
        onOpenChange={(next) => !next && setImpact(null)}
        title="재고에 반영할까요?"
        description={
          impact
            ? `전체 재고 ${impact.totalInventoryCount}개 중 ${impact.affectedInventoryCount}개에 바뀐 값이 반영됩니다. 모든 변경 필드에 재고값을 직접 지정한 ${impact.fullyProtectedInventoryCount}개는 그대로 유지됩니다. ${impact.fieldImpacts.map((field) => `${impactFieldLabels[field.field] ?? field.field}: 반영 ${field.affectedInventoryCount}개, 보호 ${field.protectedInventoryCount}개`).join('; ')}`
            : ''
        }
        confirmLabel="반영하고 저장"
        pending={updateMutation.isPending}
        onConfirm={() => frozen && updateMutation.mutate(frozen)}
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
    queryKey: ['category', 'list', itemId],
    queryFn: () =>
      collectPages((cursor) => inventoryApi.categories({ itemId, limit: 100, cursor })),
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
      key={itemQuery.data.id}
      item={itemQuery.data}
      categories={categoriesQuery.data?.items ?? []}
    />
  )
}
