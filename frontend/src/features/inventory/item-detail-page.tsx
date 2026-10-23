import { useQuery } from '@tanstack/react-query'
import { PackagePlus } from 'lucide-react'
import { Link, useParams } from 'react-router-dom'
import { inventoryApi } from './api'
import { useAddInventory } from './add-inventory-context'
import { InventoryCard } from './components/inventory-card'
import { ItemImages } from '@/features/media/item-images'
import { useAuth } from '@/features/auth/auth-provider'
import { Alert } from '@/shared/ui/alert'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { MetaText, PageTitle, SectionTitle } from '@/shared/ui/typography'
import { formatPrice } from '@/shared/lib/format'
import { canManage, permissionLabel } from '@/shared/lib/permission'

export function ItemDetailPage() {
  const { itemId = '' } = useParams()
  const { openAdd } = useAddInventory()
  const { user, space } = useAuth()
  const owner = Boolean(user && space && user.id === space.ownerUserId)

  const itemQuery = useQuery({
    queryKey: ['item', 'detail', itemId],
    queryFn: () => inventoryApi.item(itemId),
    enabled: Boolean(itemId),
  })
  const inventoriesQuery = useQuery({
    queryKey: ['item', 'inventories', itemId],
    queryFn: () => inventoryApi.itemInventories(itemId),
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
    return (
      <div className="flex flex-col items-start gap-3">
        <Alert variant="error">물건을 찾을 수 없습니다.</Alert>
        <Button asChild variant="secondary">
          <Link to="/inventory">재고 목록으로</Link>
        </Button>
      </div>
    )
  }

  const item = itemQuery.data
  const inventories = inventoriesQuery.data?.items ?? []
  const categoryName =
    categoriesQuery.data?.items.find((category) => category.id === item.categoryId)?.name ??
    '미분류'
  const canAdd = item.effectivePermission ? canManage(item.effectivePermission) : owner

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-1">
        <Link to="/inventory" className="text-xs font-bold underline underline-offset-2">
          ← 재고 목록
        </Link>
        <div className="flex flex-wrap items-center gap-2">
          <PageTitle>{item.name}</PageTitle>
          <Badge variant="neutral">{categoryName}</Badge>
          {item.isShared ? <Badge variant="info">공유</Badge> : null}
          {item.effectivePermission ? (
            <Badge>{permissionLabel(item.effectivePermission)}</Badge>
          ) : null}
        </div>
        <MetaText>
          같은 물건의 재고 {inventories.length}개 · {formatPrice(item.priceAmount)}
        </MetaText>
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="flex flex-col gap-4">
          <Card>
            <CardBody className="gap-3">
              <SectionTitle>물건 정보</SectionTitle>
              <dl className="flex flex-col gap-2 text-xs">
                <div className="flex justify-between gap-3">
                  <dt className="text-foreground/60 font-bold">이름</dt>
                  <dd className="font-black">{item.name}</dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-foreground/60 font-bold">카테고리</dt>
                  <dd className="font-black">{categoryName}</dd>
                </div>
                <div className="flex justify-between gap-3">
                  <dt className="text-foreground/60 font-bold">가격</dt>
                  <dd className="font-black">{formatPrice(item.priceAmount)}</dd>
                </div>
                <div className="flex flex-col gap-1">
                  <dt className="text-foreground/60 font-bold">설명</dt>
                  <dd className="font-medium">{item.description?.trim() || '설명 없음'}</dd>
                </div>
              </dl>
              {item.effectivePermission === 'OWNER' ? (
                <Button asChild variant="secondary">
                  <Link to={`/items/${item.id}/edit`}>물건 정보 수정</Link>
                </Button>
              ) : null}
            </CardBody>
          </Card>

          <Card>
            <CardBody className="gap-3">
              <SectionTitle>사진</SectionTitle>
              <ItemImages itemId={item.id} canUpload={item.effectivePermission === 'OWNER'} />
            </CardBody>
          </Card>
        </div>

        <Card>
          <CardBody className="gap-3">
            <SectionTitle>이 물건의 재고</SectionTitle>
            <p className="text-foreground/70 text-xs">
              같은 물건을 여러 곳에 두면 재고를 추가하세요.
            </p>
            {canAdd ? (
              <Button
                type="button"
                onClick={() =>
                  openAdd({ itemId: item.id, source: item.isShared ? 'shared' : 'owned' })
                }
              >
                <PackagePlus aria-hidden="true" className="size-4" />
                재고 추가
              </Button>
            ) : (
              <p className="text-foreground/60 text-xs">재고를 추가할 권한이 없습니다.</p>
            )}
          </CardBody>
        </Card>
      </div>

      <section className="flex flex-col gap-3">
        <SectionTitle aside={<span>{inventories.length}개</span>}>연결된 재고</SectionTitle>
        {inventoriesQuery.isLoading ? (
          <p role="status" className="text-xs">
            재고를 불러오는 중…
          </p>
        ) : inventoriesQuery.isError ? (
          <Alert variant="error">재고를 불러오지 못했습니다.</Alert>
        ) : inventories.length === 0 ? (
          <Card>
            <CardBody className="items-start gap-2">
              <p className="text-sm font-black">아직 이 물건의 재고가 없어요.</p>
              {canAdd ? (
                <Button
                  type="button"
                  size="sm"
                  onClick={() =>
                    openAdd({ itemId: item.id, source: item.isShared ? 'shared' : 'owned' })
                  }
                >
                  첫 재고 추가
                </Button>
              ) : null}
            </CardBody>
          </Card>
        ) : (
          <div className="grid gap-3 md:grid-cols-2">
            {inventories.map((inventory) => (
              <InventoryCard key={inventory.id} inventory={inventory} />
            ))}
          </div>
        )}
      </section>
    </div>
  )
}
