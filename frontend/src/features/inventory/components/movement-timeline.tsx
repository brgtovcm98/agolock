import { useQuery } from '@tanstack/react-query'
import { inventoryApi, type Inventory } from '../api'
import { Card, CardBody } from '@/shared/ui/card'
import { SectionTitle } from '@/shared/ui/typography'
import { formatDate } from '@/shared/lib/format'

export function MovementTimeline({ inventory }: { inventory: Inventory }) {
  const movementsQuery = useQuery({
    queryKey: ['inventory', 'movements', inventory.id],
    queryFn: () => inventoryApi.movements(inventory.id),
    enabled: !inventory.isShared,
  })

  const movements = movementsQuery.data ?? []

  return (
    <Card>
      <CardBody className="gap-3">
        <SectionTitle aside={<span>{movements.length}회</span>}>이동 이력</SectionTitle>
        {movementsQuery.isLoading ? (
          <p role="status" className="text-xs">
            불러오는 중…
          </p>
        ) : movementsQuery.isError ? (
          <p className="text-foreground/60 text-xs">이동 이력을 불러오지 못했습니다.</p>
        ) : (
          <ol className="flex flex-col gap-2">
            {movements.map((movement) => (
              <li key={movement.id} className="flex gap-2 text-xs">
                <span className="bg-primary mt-1 size-2 shrink-0" aria-hidden="true" />
                <div className="flex min-w-0 flex-col gap-0.5">
                  <span className="font-black">
                    {movement.from.name} → {movement.to.name}
                  </span>
                  <span className="text-foreground/60">{formatDate(movement.movedAt)}</span>
                </div>
              </li>
            ))}
            <li className="flex gap-2 text-xs">
              <span className="bg-success mt-1 size-2 shrink-0" aria-hidden="true" />
              <div className="flex min-w-0 flex-col gap-0.5">
                <span className="font-black">등록 · {inventory.location.name}</span>
                <span className="text-foreground/60">{formatDate(inventory.createdAt)}</span>
              </div>
            </li>
          </ol>
        )}
      </CardBody>
    </Card>
  )
}
