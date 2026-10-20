import { Link } from 'react-router-dom'
import { useAuth } from '@/features/auth/auth-provider'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { Avatar } from '@/shared/ui/avatar'
import { MetaText, PageTitle, SectionTitle } from '@/shared/ui/typography'

const m1Items = [
  '이메일·비밀번호 가입과 로그인',
  'Spring Session JDBC 세션과 CSRF',
  '초대코드 가입 제한과 비밀번호 재설정',
  '내 프로필·Space 이름·화면 모드 설정',
]

const nextSteps = [
  'M2: Room·Shelf·Box 계층과 유효 권한',
  'M3: Category·Item·Inventory와 이동 이력',
  'M4: 초대 전용 공유와 권한 관리',
]

export function HomePage() {
  const { user, space } = useAuth()
  const environment = import.meta.env.VITE_APP_ENV ?? import.meta.env.MODE

  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-col gap-4">
        <div className="flex flex-wrap items-end justify-between gap-3">
          <div className="flex flex-col gap-2">
            <p className="label-caps text-foreground/70">AGO LOCK · M1</p>
            <PageTitle>{user?.displayName ?? '반가워요'}님, 반가워요</PageTitle>
            <MetaText>
              {space?.name ?? '내 Space'}에서 물건을 정리할 준비가 됐어요. 프로필과 Space 이름은
              설정에서 바꿀 수 있습니다.
            </MetaText>
          </div>
          <Badge variant="info">{environment}</Badge>
        </div>

        <Card>
          <CardBody className="gap-4">
            <div className="flex items-center gap-3">
              <Avatar name={user?.displayName ?? '?'} />
              <div className="min-w-0">
                <p className="truncate text-sm font-black">{user?.displayName}</p>
                <p className="text-foreground/60 truncate text-xs">{user?.email}</p>
              </div>
              <Badge variant="info" className="ml-auto">
                {space?.name ?? '내 Space'}
              </Badge>
            </div>
            <div className="flex flex-wrap gap-2">
              <Button asChild>
                <Link to="/settings">설정 열기</Link>
              </Button>
              <Button asChild variant="secondary">
                <Link to="/space">공간</Link>
              </Button>
              <Button asChild variant="secondary">
                <Link to="/inventory">재고</Link>
              </Button>
              {import.meta.env.DEV ? (
                <Button asChild variant="secondary">
                  <Link to="/design-preview?screen=home">디자인 미리보기</Link>
                </Button>
              ) : null}
            </div>
          </CardBody>
        </Card>
      </section>

      <div className="grid gap-4 md:grid-cols-2">
        <section className="flex flex-col gap-3">
          <SectionTitle>M1에서 연결된 것</SectionTitle>
          <ul className="flex flex-col gap-2">
            {m1Items.map((item) => (
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
          <SectionTitle>다음 단계</SectionTitle>
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
