import { Link } from 'react-router-dom'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { PageTitle, MetaText } from '@/shared/ui/typography'

export function PlaceholderPage({
  title,
  description,
  milestone,
}: {
  title: string
  description: string
  milestone: string
}) {
  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <p className="label-caps text-foreground/70">{milestone}</p>
        <PageTitle>{title}</PageTitle>
        <MetaText>{description}</MetaText>
      </div>

      <Card>
        <CardBody className="items-start gap-4">
          <p className="border-border bg-sunken border-[2.5px] border-dashed p-4 text-xs font-bold">
            이 화면은 현재 셸과 내비게이션만 확인합니다. 실제 API와 데이터는 다음 마일스톤에서
            연결됩니다.
          </p>
          <div className="flex flex-wrap gap-2">
            <Button asChild>
              <Link to="/design-preview">채택 디자인 미리보기</Link>
            </Button>
            <Button asChild variant="secondary">
              <Link to="/">M1 홈으로</Link>
            </Button>
          </div>
        </CardBody>
      </Card>
    </div>
  )
}
