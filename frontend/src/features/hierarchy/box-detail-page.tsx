import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { Box as BoxIcon } from 'lucide-react'
import { hierarchyApi } from './api'
import { LocationInventories } from '@/features/inventory/location-inventories'
import { Alert } from '@/shared/ui/alert'
import { Badge } from '@/shared/ui/badge'
import { MetaText, PageTitle } from '@/shared/ui/typography'
import { permissionLabel } from '@/shared/lib/permission'

export function BoxDetailPage() {
  const { boxId = '' } = useParams()
  const boxQuery = useQuery({
    queryKey: ['hierarchy', 'box', boxId],
    queryFn: () => hierarchyApi.getBox(boxId),
    enabled: Boolean(boxId),
  })
  const shelfQuery = useQuery({
    queryKey: ['hierarchy', 'shelf', boxQuery.data?.shelfId],
    queryFn: () => hierarchyApi.getShelf(boxQuery.data?.shelfId ?? ''),
    enabled: Boolean(boxQuery.data?.shelfId),
  })

  if (boxQuery.isLoading)
    return (
      <p role="status" className="text-sm font-bold">
        Box를 불러오는 중…
      </p>
    )
  if (boxQuery.isError || !boxQuery.data)
    return <Alert variant="error">Box를 찾을 수 없습니다.</Alert>

  const box = boxQuery.data
  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-1">
        <Link to="/space" className="text-xs font-bold underline underline-offset-2">
          ← 공간 지도
        </Link>
        <p className="label-caps text-foreground/60">공간</p>
        <div className="flex flex-wrap items-center gap-2">
          <BoxIcon aria-hidden="true" className="size-7" />
          <PageTitle>{box.name}</PageTitle>
          <Badge variant="info">{permissionLabel(box.effectivePermission)}</Badge>
          <Badge>재고 {box.inventoryCount ?? 0}</Badge>
        </div>
        {shelfQuery.data ? (
          <MetaText>
            {shelfQuery.data.name} 아래 · {box.description ?? '설명 없음'}
          </MetaText>
        ) : box.description ? (
          <MetaText>{box.description}</MetaText>
        ) : null}
      </div>

      {shelfQuery.data ? (
        <LocationInventories
          roomId={shelfQuery.data.roomId}
          shelfId={box.shelfId}
          boxId={box.id}
          permission={box.effectivePermission}
          isShared={box.isShared}
        />
      ) : (
        <p role="status" className="text-xs">
          위치 정보를 불러오는 중…
        </p>
      )}
    </div>
  )
}
