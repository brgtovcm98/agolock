import { Link } from 'react-router-dom'
import { Brand } from '@/shared/ui/brand'
import { Button } from '@/shared/ui/button'
import { PageTitle } from '@/shared/ui/typography'

export function NotFoundPage() {
  return (
    <div className="bg-background grid min-h-dvh place-items-center px-4 py-12">
      <div className="border-border bg-card shadow-brutal flex w-full max-w-lg flex-col gap-6 border-[2.5px] p-6">
        <Brand />
        <p className="text-7xl leading-none font-black tracking-[-0.06em]">404</p>
        <PageTitle className="text-2xl">화면을 찾지 못했어요</PageTitle>
        <p className="text-foreground/80 text-sm">
          주소가 바뀌었거나 삭제된 화면입니다. 홈에서 다시 시작해 주세요.
        </p>
        <Button asChild>
          <Link to="/">홈으로</Link>
        </Button>
      </div>
    </div>
  )
}
