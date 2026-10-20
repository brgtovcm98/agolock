import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { Box as BoxIcon } from 'lucide-react'
import { hierarchyApi } from './api'
import { Alert } from '@/shared/ui/alert'
import { Badge } from '@/shared/ui/badge'
import { Card, CardBody } from '@/shared/ui/card'
import { MetaText, PageTitle, SectionTitle } from '@/shared/ui/typography'

export function ShelfDetailPage() {
  const { shelfId = '' } = useParams()
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
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-1">
        <Link to="/space" className="text-xs font-bold underline underline-offset-2">
          ← 공간 지도
        </Link>
        <p className="label-caps text-foreground/60">SCR-006 · Shelf</p>
        <div className="flex flex-wrap items-center gap-2">
          <PageTitle>{shelf.name}</PageTitle>
          <Badge variant="info">{shelf.effectivePermission}</Badge>
          <Badge>Box {shelf.boxCount}</Badge>
        </div>
        {shelf.description ? <MetaText>{shelf.description}</MetaText> : null}
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
    </div>
  )
}
