import { useQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Box as BoxIcon } from 'lucide-react'
import { hierarchyApi } from './api'
import { LocationInventories } from '@/features/inventory/location-inventories'
import { ShareDialog } from '@/features/sharing/share-dialog'
import { Alert } from '@/shared/ui/alert'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { MetaText, PageTitle, SectionTitle } from '@/shared/ui/typography'
import { permissionLabel } from '@/shared/lib/permission'

export function ShelfDetailPage() {
  const { shelfId = '' } = useParams()
  const [shelfShareOpen, setShelfShareOpen] = useState(false)
  const shelfQuery = useQuery({
    queryKey: ['hierarchy', 'shelf', shelfId],
    queryFn: () => hierarchyApi.getShelf(shelfId),
    enabled: Boolean(shelfId),
  })
  const boxesQuery = useQuery({
    queryKey: ['hierarchy', 'boxes', shelfId],
    queryFn: () => hierarchyApi.listBoxes(shelfId),
    enabled: Boolean(shelfId),
  })

  if (shelfQuery.isLoading)
    return (
      <p role="status" className="text-sm font-bold">
        Shelf를 불러오는 중…
      </p>
    )
  if (shelfQuery.isError || !shelfQuery.data)
    return <Alert variant="error">Shelf를 찾을 수 없습니다.</Alert>

  const shelf = shelfQuery.data
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <Link to="/space" className="text-xs font-bold underline underline-offset-2">
          ← 공간 지도
        </Link>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div className="flex min-w-0 flex-col gap-1">
            <p className="label-caps text-foreground/60">공간</p>
            <div className="flex flex-wrap items-center gap-2">
              <PageTitle>{shelf.name}</PageTitle>
              <Badge variant="info">{permissionLabel(shelf.effectivePermission)}</Badge>
              <Badge>Box {shelf.boxCount}</Badge>
              <Badge>재고 {shelf.inventoryCount ?? 0}</Badge>
            </div>
            {shelf.description ? <MetaText>{shelf.description}</MetaText> : null}
          </div>
          {shelf.effectivePermission === 'OWNER' ? (
            <Button type="button" variant="secondary" onClick={() => setShelfShareOpen(true)}>
              공유
            </Button>
          ) : null}
        </div>
      </div>
      <Card>
        <CardBody className="gap-3">
          <SectionTitle>Box</SectionTitle>
          {boxesQuery.isLoading ? (
            <p className="text-xs" role="status">
              불러오는 중…
            </p>
          ) : null}
          {boxesQuery.data?.items.length === 0 ? (
            <p className="text-foreground/70 text-xs">Box가 없습니다.</p>
          ) : null}
          <div className="grid gap-2 md:grid-cols-2">
            {boxesQuery.data?.items.map((box) => (
              <Link
                key={box.id}
                to={`/space/boxes/${box.id}`}
                className="border-border bg-blue-soft flex items-center gap-2 border-[2.5px] px-3 py-2 text-sm font-black"
              >
                <BoxIcon aria-hidden="true" className="size-4" />
                {box.name}
              </Link>
            ))}
          </div>
        </CardBody>
      </Card>

      <LocationInventories
        roomId={shelf.roomId}
        shelfId={shelf.id}
        permission={shelf.effectivePermission}
        isShared={shelf.isShared}
      />

      {shelfShareOpen ? (
        <ShareDialog
          target={{ shelfId: shelf.id, name: shelf.name }}
          onClose={() => setShelfShareOpen(false)}
        />
      ) : null}
    </div>
  )
}
