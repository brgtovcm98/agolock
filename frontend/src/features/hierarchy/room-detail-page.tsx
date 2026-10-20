import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { hierarchyApi } from './api'
import { Alert } from '@/shared/ui/alert'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { MetaText, PageTitle, SectionTitle } from '@/shared/ui/typography'

export function RoomDetailPage() {
  const { roomId = '' } = useParams()
  const roomQuery = useQuery({
    queryKey: ['hierarchy', 'room', roomId],
    queryFn: () => hierarchyApi.getRoom(roomId),
    enabled: Boolean(roomId),
  })
  const shelvesQuery = useQuery({
    queryKey: ['hierarchy', 'shelves', roomId],
    queryFn: () => hierarchyApi.listShelves(roomId),
    enabled: Boolean(roomId),
  })

  if (roomQuery.isLoading) {
    return (
      <p role="status" className="text-sm font-bold">
        Room을 불러오는 중…
      </p>
    )
  }
  if (roomQuery.isError || !roomQuery.data) {
    return <Alert variant="error">Room을 찾을 수 없습니다.</Alert>
  }

  const room = roomQuery.data
  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-1">
        <Link to="/space" className="text-xs font-bold underline underline-offset-2">
          ← 공간 지도
        </Link>
        <p className="label-caps text-foreground/60">SCR-005 · Room</p>
        <div className="flex flex-wrap items-center gap-2">
          <PageTitle>{room.name}</PageTitle>
          <Badge variant="info">{room.effectivePermission}</Badge>
          <Badge>Shelf {room.shelfCount}</Badge>
        </div>
        {room.description ? <MetaText>{room.description}</MetaText> : null}
      </div>

      <Card>
        <CardBody className="gap-3">
          <SectionTitle>Shelf</SectionTitle>
          {shelvesQuery.isLoading ? (
            <p className="text-xs" role="status">
              불러오는 중…
            </p>
          ) : null}
          {shelvesQuery.data?.items.length === 0 ? (
            <p className="text-foreground/70 text-xs">
              Shelf가 없습니다. 공간 지도에서 추가할 수 있어요.
            </p>
          ) : null}
          <div className="flex flex-col gap-2">
            {shelvesQuery.data?.items.map((shelf) => (
              <Link
                key={shelf.id}
                to={`/space/shelves/${shelf.id}`}
                className="border-border bg-yellow-soft flex items-center justify-between gap-2 border-[2.5px] px-3 py-2 text-sm font-black"
              >
                {shelf.name}
                <span className="text-xs">Box {shelf.boxCount}</span>
              </Link>
            ))}
          </div>
          <Button asChild variant="secondary">
            <Link to="/space">공간 지도로 돌아가기</Link>
          </Button>
        </CardBody>
      </Card>
    </div>
  )
}
