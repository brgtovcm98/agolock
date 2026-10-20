import { Link } from 'react-router-dom'
import { PageTitle, MetaText } from '@/shared/ui/typography'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'

const checklist = [
  'Docker Compose 인프라 4종 healthy',
  'Spring Boot·MyBatis·Flyway·Testcontainers',
  'React·Vite·Tailwind·E안 디자인 토큰',
  '계약 미리보기 생성기와 docs 검사',
  'docs·backend·frontend·contract CI job 작성',
]

const nextSteps = [
  'M1: 가입·로그인·세션·CSRF·Space 설정',
  'M2: Room·Shelf·Box 계층과 유효 권한',
  'M3: Category·Item·Inventory와 이동 이력',
]

export function HomePage() {
  const environment = import.meta.env.VITE_APP_ENV ?? import.meta.env.MODE

  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-col gap-4">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div className="flex flex-col gap-2">
            <p className="label-caps text-foreground/70">AGO LOCK · M0</p>
            <PageTitle>기반이 준비됐어요</PageTitle>
            <MetaText>
              인증과 도메인 API는 M1부터 연결합니다. 지금은 빌드·인프라·계약·디자인 기반만
              확인합니다.
            </MetaText>
          </div>
          <Badge variant="info">{environment}</Badge>
        </div>

        <Card>
          <CardBody className="gap-4">
            <div className="flex flex-wrap gap-2">
              <Button asChild>
                <Link to="/design-preview?screen=home">디자인 미리보기</Link>
              </Button>
              <Button asChild variant="secondary">
                <Link to="/space">공간 화면 자리 보기</Link>
              </Button>
              <Button asChild variant="secondary">
                <Link to="/inventory">재고 화면 자리 보기</Link>
              </Button>
            </div>
            <p className="text-foreground/70 text-xs">
              미리보기는 실제 데이터가 아닌 E안 시안 재현용 목데이터를 사용합니다.
            </p>
          </CardBody>
        </Card>
      </section>

      <div className="grid gap-4 md:grid-cols-2">
        <section className="flex flex-col gap-3">
          <h2 className="border-border border-b-[2.5px] pb-2 text-xs font-black tracking-[0.08em]">
            M0에서 확인한 것
          </h2>
          <ul className="flex flex-col gap-2">
            {checklist.map((item) => (
              <li
                key={item}
                className="border-border bg-card flex items-start gap-2 border-[2.5px] p-3 text-xs font-bold"
              >
                <span
                  aria-hidden="true"
                  className="bg-success mt-0.5 inline-block size-3 shrink-0"
                />
                {item}
              </li>
            ))}
          </ul>
        </section>

        <section className="flex flex-col gap-3">
          <h2 className="border-border border-b-[2.5px] pb-2 text-xs font-black tracking-[0.08em]">
            다음 단계
          </h2>
          <ol className="flex flex-col gap-2">
            {nextSteps.map((item, index) => (
              <li
                key={item}
                className="border-border bg-card flex items-start gap-2 border-[2.5px] p-3 text-xs font-bold"
              >
                <span className="bg-accent text-on-accent inline-grid size-5 shrink-0 place-items-center text-[10px] font-black">
                  {index + 1}
                </span>
                {item}
              </li>
            ))}
          </ol>
        </section>
      </div>
    </div>
  )
}
