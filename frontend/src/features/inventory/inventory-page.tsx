import { useInfiniteQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { useState } from 'react'
import { inventoryApi, type InventoryStatus } from './api'
import { useAddInventory } from './add-inventory-context'
import { InventoryCard } from './components/inventory-card'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { PageHeader } from '@/shared/ui/page-header'

type FilterId = 'all' | InventoryStatus

const filters: Array<{ id: FilterId; label: string; statuses?: InventoryStatus[] }> = [
  { id: 'all', label: '전체' },
  { id: 'IN_USE', label: '사용중', statuses: ['IN_USE'] },
  { id: 'STORED', label: '보관', statuses: ['STORED'] },
  { id: 'DISPOSED', label: '폐기', statuses: ['DISPOSED'] },
]

export function InventoryPage() {
  const { openAdd } = useAddInventory()
  const [filter, setFilter] = useState<FilterId>('all')
  const statuses = filters.find((item) => item.id === filter)?.statuses

  const inventoriesQuery = useInfiniteQuery({
    queryKey: ['inventory', 'list', { scope: 'ALL', statuses: statuses ?? [] }],
    queryFn: ({ pageParam }) =>
      inventoryApi.inventories({
        statuses,
        scope: 'ALL',
        limit: 20,
        cursor: pageParam,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (lastPage) => lastPage.nextCursor ?? undefined,
  })

  const inventories = inventoriesQuery.data?.pages.flatMap((page) => page.items) ?? []
  const hasNextPage = Boolean(inventoriesQuery.hasNextPage)

  return (
    <div className="flex flex-col gap-6">
      <PageHeader title="재고" description="등록한 물건을 찾고, 위치와 상태를 관리합니다." />

      <div className="flex flex-wrap gap-2" role="group" aria-label="재고 상태 필터">
        {filters.map((item) => (
          <button
            key={item.id}
            type="button"
            aria-pressed={filter === item.id}
            onClick={() => setFilter(item.id)}
            className={
              filter === item.id
                ? 'border-border bg-foreground text-background h-11 border-[2.5px] px-3 text-xs font-black transition-colors duration-150'
                : 'border-border bg-card hover:bg-sunken h-11 border-[2.5px] px-3 text-xs font-black transition-colors duration-150'
            }
          >
            {item.label}
          </button>
        ))}
      </div>

      {inventoriesQuery.isLoading ? (
        <Card>
          <CardBody>
            <p role="status" className="text-sm font-bold">
              재고를 불러오는 중…
            </p>
          </CardBody>
        </Card>
      ) : inventoriesQuery.isError ? (
        <Card>
          <CardBody className="items-start gap-3">
            <Alert variant="error">재고를 불러오지 못했습니다.</Alert>
            <Button
              type="button"
              variant="secondary"
              onClick={() => void inventoriesQuery.refetch()}
            >
              다시 시도
            </Button>
          </CardBody>
        </Card>
      ) : inventories.length === 0 ? (
        <Card>
          <CardBody className="items-start gap-3">
            <p className="text-lg font-black">
              {filter === 'all' ? '아직 등록한 물건이 없어요' : '이 상태의 재고가 없어요'}
            </p>
            <p className="text-foreground/70 text-xs">
              {filter === 'all'
                ? '지금 있는 위치를 골라 첫 물건을 등록해 보세요.'
                : '다른 상태를 선택하거나 새 물건을 등록해 보세요.'}
            </p>
            <div className="flex flex-wrap gap-2">
              <Button type="button" onClick={() => openAdd()}>
                <Plus aria-hidden="true" className="size-4" />첫 물건 등록
              </Button>
              {filter !== 'all' ? (
                <Button type="button" variant="secondary" onClick={() => setFilter('all')}>
                  전체 보기
                </Button>
              ) : null}
            </div>
          </CardBody>
        </Card>
      ) : (
        <div className="flex flex-col gap-3">
          <div className="grid grid-cols-[minmax(0,1fr)] gap-3 md:grid-cols-2">
            {inventories.map((inventory) => (
              <InventoryCard key={inventory.id} inventory={inventory} />
            ))}
          </div>
          {hasNextPage ? (
            <Button
              type="button"
              variant="secondary"
              disabled={inventoriesQuery.isFetchingNextPage}
              onClick={() => void inventoriesQuery.fetchNextPage()}
            >
              {inventoriesQuery.isFetchingNextPage ? '불러오는 중…' : '더 보기'}
            </Button>
          ) : null}
        </div>
      )}
    </div>
  )
}
