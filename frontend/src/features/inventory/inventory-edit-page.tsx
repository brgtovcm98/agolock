import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import {
  inventoryApi,
  type Category,
  type Inventory,
  type Item,
} from './api'
import { ApiError } from '@/shared/api/client'
import { Alert } from '@/shared/ui/alert'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { MetaText, PageTitle, SectionTitle } from '@/shared/ui/typography'
import { TextField } from '@/shared/ui/text-field'
import { formatPrice } from '@/shared/lib/format'
import { canEdit } from '@/shared/lib/permission'

function OverrideField({
  enabled,
  label,
  master,
  onToggle,
  children,
}: {
  enabled: boolean
  label: string
  master: string
  onToggle: (next: boolean) => void
  children: React.ReactNode
}) {
  return (
    <div className="border-border flex flex-col gap-2 border-2 p-3">
      <label className="flex items-center gap-2">
        <input
          type="checkbox"
          checked={enabled}
          onChange={(event) => onToggle(event.target.checked)}
          className="accent-primary size-4"
        />
        <span className="text-xs font-black">{label}</span>
        <Badge variant="neutral" className="ml-auto h-6 text-[10px]">
          마스터: {master}
        </Badge>
      </label>
      <div className={enabled ? '' : 'pointer-events-none opacity-40'}>{children}</div>
    </div>
  )
}

function InventoryEditForm({
  inventory,
  item,
  categories,
}: {
  inventory: Inventory
  item: Item | undefined
  categories: Category[]
}) {
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [error, setError] = useState<string | null>(null)
  const [nameOn, setNameOn] = useState(inventory.nameOverride !== null)
  const [name, setName] = useState(inventory.nameOverride ?? inventory.displayName)
  const [descriptionOn, setDescriptionOn] = useState(inventory.descriptionOverride !== null)
  const [description, setDescription] = useState(
    inventory.descriptionOverride ?? inventory.description ?? '',
  )
  const [categoryOn, setCategoryOn] = useState(inventory.categoryIdOverride !== null)
  const [categoryId, setCategoryId] = useState(
    inventory.categoryIdOverride ?? inventory.categoryId ?? '',
  )
  const [priceOn, setPriceOn] = useState(inventory.priceAmountOverride !== null)
  const [price, setPrice] = useState(
    inventory.priceAmountOverride !== null
      ? String(inventory.priceAmountOverride)
      : inventory.priceAmount !== null
        ? String(inventory.priceAmount)
        : '',
  )

  const itemCategoryName =
    categories.find((category) => category.id === item?.categoryId)?.name ?? '미분류'

  const mutation = useMutation({
    mutationFn: async () => {
      if (nameOn && !name.trim()) throw new Error('재고 이름을 입력해 주세요.')
      if (priceOn && !price.trim()) throw new Error('재고 가격을 입력해 주세요.')
      const priceAmount = priceOn ? Number(price) : null
      if (priceAmount !== null && (!Number.isInteger(priceAmount) || priceAmount < 0)) {
        throw new Error('가격은 0원 이상의 정수로 입력해 주세요.')
      }
      return inventoryApi.updateInventory(inventory.id, {
        version: inventory.version,
        nameOverride: nameOn ? name.trim() : null,
        descriptionOverride: descriptionOn ? description : null,
        categoryIdOverride: categoryOn ? categoryId || null : null,
        priceAmountOverride: priceAmount,
      })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['inventory'] })
      navigate(`/inventory/${inventory.id}`, { replace: true })
    },
    onError: async (cause) => {
      if (cause instanceof ApiError && cause.status === 409) {
        setError('다른 곳에서 먼저 수정했어요. 최신 정보를 불러온 뒤 다시 저장해 주세요.')
        await queryClient.invalidateQueries({ queryKey: ['inventory', 'detail', inventory.id] })
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

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-1">
        <Link
          to={`/inventory/${inventory.id}`}
          className="text-xs font-bold underline underline-offset-2"
        >
          ← 재고 상세
        </Link>
        <p className="label-caps text-foreground/60">{inventory.displayName}</p>
        <PageTitle>재고 편집</PageTitle>
        <MetaText>마스터 값과 다르게 표시할 값만 직접 입력하세요.</MetaText>
      </div>

      {error ? <Alert variant="error">{error}</Alert> : null}

      <form
        className="flex flex-col gap-4"
        onSubmit={(event) => {
          event.preventDefault()
          mutation.mutate()
        }}
      >
        <Card>
          <CardBody className="gap-3">
            <SectionTitle>재고 값</SectionTitle>

            <OverrideField
              enabled={nameOn}
              label="이름"
              master={item?.name ?? inventory.itemName}
              onToggle={setNameOn}
            >
              <TextField
                label="재고 이름"
                value={name}
                maxLength={120}
                disabled={!nameOn}
                onChange={(event) => setName(event.target.value)}
              />
            </OverrideField>

            <OverrideField
              enabled={descriptionOn}
              label="설명"
              master={item?.description ?? '설명 없음'}
              onToggle={setDescriptionOn}
            >
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-black">재고 설명</span>
                <textarea
                  value={description}
                  maxLength={2000}
                  disabled={!descriptionOn}
                  onChange={(event) => setDescription(event.target.value)}
                  rows={3}
                  className="border-border bg-card min-h-20 w-full border-[2.5px] px-3 py-2 text-sm font-medium outline-none disabled:opacity-50"
                />
              </label>
            </OverrideField>

            <OverrideField
              enabled={categoryOn}
              label="카테고리"
              master={itemCategoryName}
              onToggle={setCategoryOn}
            >
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-black">재고 카테고리</span>
                <select
                  aria-label="재고 카테고리"
                  value={categoryId}
                  disabled={!categoryOn}
                  onChange={(event) => setCategoryId(event.target.value)}
                  className="border-border bg-card h-12 border-[2.5px] px-3 text-sm disabled:opacity-50"
                >
                  <option value="">마스터 값 사용</option>
                  {categories.map((category) => (
                    <option key={category.id} value={category.id}>
                      {category.name}
                    </option>
                  ))}
                </select>
              </label>
            </OverrideField>

            <OverrideField
              enabled={priceOn}
              label="가격"
              master={formatPrice(item?.priceAmount)}
              onToggle={setPriceOn}
            >
              <TextField
                label="재고 가격"
                type="number"
                min={0}
                inputMode="numeric"
                value={price}
                disabled={!priceOn}
                onChange={(event) => setPrice(event.target.value)}
              />
            </OverrideField>
          </CardBody>
        </Card>

        <div className="flex justify-end gap-2">
          <Button asChild variant="secondary">
            <Link to={`/inventory/${inventory.id}`}>취소</Link>
          </Button>
          <Button type="submit" disabled={mutation.isPending}>
            {mutation.isPending ? '저장 중…' : '저장'}
          </Button>
        </div>
      </form>
    </div>
  )
}

export function InventoryEditPage() {
  const { inventoryId = '' } = useParams()
  const inventoryQuery = useQuery({
    queryKey: ['inventory', 'detail', inventoryId],
    queryFn: () => inventoryApi.inventory(inventoryId),
    enabled: Boolean(inventoryId),
  })
  const itemQuery = useQuery({
    queryKey: ['item', 'detail', inventoryQuery.data?.itemId],
    queryFn: () => inventoryApi.item(inventoryQuery.data?.itemId ?? ''),
    enabled: Boolean(inventoryQuery.data?.itemId),
  })
  const categoriesQuery = useQuery({
    queryKey: ['category', 'list'],
    queryFn: inventoryApi.categories,
  })

  if (inventoryQuery.isLoading) {
    return (
      <p role="status" className="text-sm font-bold">
        재고를 불러오는 중…
      </p>
    )
  }
  if (inventoryQuery.isError || !inventoryQuery.data) {
    return <Alert variant="error">재고를 찾을 수 없습니다.</Alert>
  }
  if (!canEdit(inventoryQuery.data.effectivePermission)) {
    return <Alert variant="error">이 재고를 편집할 권한이 없습니다.</Alert>
  }

  return (
    <InventoryEditForm
      key={`${inventoryQuery.data.id}-${inventoryQuery.data.version}`}
      inventory={inventoryQuery.data}
      item={itemQuery.data}
      categories={categoriesQuery.data?.items ?? []}
    />
  )
}
