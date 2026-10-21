import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { inventoryApi, type Inventory, type Permission } from './api'
import { useAddInventory } from './add-inventory-context'
import { InventoryCard } from './components/inventory-card'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { SectionTitle } from '@/shared/ui/typography'
import { canManage } from '@/shared/lib/permission'

function isDirectInventory(
  inventory: Inventory,
  location: { roomId?: string; shelfId?: string; boxId?: string },
) {
  if (location.boxId) return inventory.location.boxId === location.boxId
  if (location.shelfId) {
    return inventory.location.shelfId === location.shelfId && !inventory.location.boxId
  }
  return (
    inventory.location.roomId === location.roomId &&
    !inventory.location.shelfId &&
    !inventory.location.boxId
  )
}

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
  const canAdd = canManage(permission) && !isShared

  const inventoriesQuery = useQuery({
    queryKey: ['inventory', 'location-pool'],
    queryFn: () => inventoryApi.inventories({ scope: 'ALL', limit: 100 }),
  })

  const inventories = (inventoriesQuery.data?.items ?? []).filter((inventory) =>
    isDirectInventory(inventory, { roomId, shelfId, boxId }),
  )

  return (
    <section className="flex flex-col gap-3">
      <SectionTitle aside={<span>{inventories.length}개</span>}>직접 위치 재고</SectionTitle>
      {inventoriesQuery.isLoading ? (
        <p role="status" className="text-xs">
          재고를 불러오는 중…
        </p>
      ) : inventoriesQuery.isError ? (
        <Alert variant="error">재고를 불러오지 못했습니다.</Alert>
      ) : inventories.length === 0 ? (
        <div className="border-border bg-card flex flex-col items-start gap-2 border-[2.5px] p-4">
          <p className="text-sm font-black">이 위치에 아직 물건이 없어요.</p>
          {canAdd ? (
            <Button type="button" size="sm" onClick={() => openAdd({ roomId, shelfId, boxId })}>
              <Plus aria-hidden="true" className="size-4" />이 위치에 등록
            </Button>
          ) : null}
        </div>
      ) : (
        <>
          <div className="grid gap-3 md:grid-cols-2">
            {inventories.map((inventory) => (
              <InventoryCard key={inventory.id} inventory={inventory} />
            ))}
          </div>
          {canAdd ? (
            <Button
              type="button"
              variant="secondary"
              onClick={() => openAdd({ roomId, shelfId, boxId })}
            >
              <Plus aria-hidden="true" className="size-4" />이 위치에 등록
            </Button>
          ) : null}
        </>
      )}
      {!canAdd && isShared ? (
        <p className="text-foreground/60 text-xs">공유 위치 등록은 다음 단계에서 지원해요.</p>
      ) : null}
    </section>
  )
}
