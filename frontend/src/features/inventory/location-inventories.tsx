import { useInfiniteQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { inventoryApi, wireLocation, type Permission } from './api'
import { useAddInventory } from './add-inventory-context'
import { InventoryCard } from './components/inventory-card'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { SectionTitle } from '@/shared/ui/typography'
import { canManage } from '@/shared/lib/permission'

export function LocationInventories({
  roomId,
  shelfId,
  boxId,
  permission,
  isShared = false,
}: {
  roomId?: string
  shelfId?: string
  boxId?: string
  permission: Permission
  isShared?: boolean
}) {
  const { openAdd } = useAddInventory()
  const canAdd = canManage(permission)

  const inventoriesQuery = useInfiniteQuery({
    queryKey: ['inventory', 'location', roomId ?? '', shelfId ?? '', boxId ?? ''],
    queryFn: ({ pageParam }) =>
      inventoryApi.inventories({
        scope: 'ALL',
        limit: 20,
        ...wireLocation({ roomId, shelfId, boxId }),
        cursor: pageParam,
      }),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (page) => page.nextCursor ?? undefined,
  })

  const inventories = inventoriesQuery.data?.pages.flatMap((page) => page.items) ?? []

  return (
    <section className="flex flex-col gap-3">
      <SectionTitle aside={<span>불러온 재고 {inventories.length}개</span>}>
        직접 위치 재고
      </SectionTitle>
      {inventoriesQuery.isLoading ? (
        <p role="status" className="text-xs">
          재고를 불러오는 중…
        </p>
      ) : inventoriesQuery.isError ? (
        <Alert
          variant="error"
          action={
            <Button
              type="button"
              size="sm"
              variant="secondary"
              onClick={() => void inventoriesQuery.refetch()}
            >
              다시 시도
            </Button>
          }
        >
          재고를 불러오지 못했습니다.
        </Alert>
      ) : inventories.length === 0 ? (
        <div className="border-border bg-card flex flex-col items-start gap-2 border-[2.5px] p-4">
          <p className="text-sm font-black">이 위치에 아직 물건이 없어요.</p>
          {canAdd ? (
            <Button
              type="button"
              size="sm"
              onClick={() =>
                openAdd({ roomId, shelfId, boxId, source: isShared ? 'shared' : 'owned' })
              }
            >
              <Plus aria-hidden="true" className="size-4" />이 위치에 등록
            </Button>
          ) : null}
        </div>
      ) : (
        <>
          <div className="grid grid-cols-[minmax(0,1fr)] gap-3 md:grid-cols-2">
            {inventories.map((inventory) => (
              <InventoryCard key={inventory.id} inventory={inventory} />
            ))}
          </div>
          {canAdd ? (
            <Button
              type="button"
              variant="secondary"
              onClick={() =>
                openAdd({ roomId, shelfId, boxId, source: isShared ? 'shared' : 'owned' })
              }
            >
              <Plus aria-hidden="true" className="size-4" />이 위치에 등록
            </Button>
          ) : null}
        </>
      )}
      {inventoriesQuery.hasNextPage ? (
        <Button
          type="button"
          variant="secondary"
          disabled={inventoriesQuery.isFetchingNextPage}
          onClick={() => void inventoriesQuery.fetchNextPage()}
        >
          재고 더 보기
        </Button>
      ) : null}
    </section>
  )
}
