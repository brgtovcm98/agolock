import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { Box as BoxIcon } from 'lucide-react'
import { hierarchyApi } from './api'
import { Alert } from '@/shared/ui/alert'
import { Badge } from '@/shared/ui/badge'
import { Card, CardBody } from '@/shared/ui/card'
import { MetaText, PageTitle } from '@/shared/ui/typography'

export function BoxDetailPage() {
  const { boxId = '' } = useParams()
  const boxQuery = useQuery({
    queryKey: ['hierarchy', 'box', boxId],
    queryFn: () => hierarchyApi.getBox(boxId),
    enabled: Boolean(boxId),
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
      <Link to="/space" className="text-xs font-bold underline underline-offset-2">
        ← 공간 지도
      </Link>
      <div className="flex flex-wrap items-center gap-2">
        <BoxIcon aria-hidden="true" className="size-7" />
        <PageTitle>{box.name}</PageTitle>
        <Badge variant="info">{box.effectivePermission}</Badge>
      </div>
      {box.description ? <MetaText>{box.description}</MetaText> : null}
      <Card>
        <CardBody className="gap-2">
          <p className="text-sm font-black">직접 위치 재고</p>
          <p className="text-foreground/70 text-xs">재고 관리는 M3에서 연결됩니다.</p>
        </CardBody>
      </Card>
    </div>
  )
}
