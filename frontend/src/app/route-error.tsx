import { isRouteErrorResponse, Link, useRouteError } from 'react-router-dom'
import { Brand } from '@/shared/ui/brand'
import { Button } from '@/shared/ui/button'
import { PageTitle } from '@/shared/ui/typography'

export function RouteError() {
  const error = useRouteError()
  const status = isRouteErrorResponse(error) ? error.status : 500
  const message =
    status === 404
      ? '요청한 화면을 찾지 못했어요.'
      : '화면을 표시하는 중 문제가 생겼어요. 잠시 후 다시 시도해 주세요.'

  return (
    <div className="bg-background grid min-h-dvh place-items-center px-4 py-10">
      <div className="border-border bg-card shadow-brutal flex w-full max-w-lg flex-col gap-5 border-[2.5px] p-6">
        <Brand />
        <p className="text-6xl leading-none font-black tracking-[-0.06em]">{status}</p>
        <PageTitle className="text-2xl">문제가 생겼어요</PageTitle>
        <p className="text-foreground/80 text-sm">{message}</p>
        <div className="flex flex-wrap gap-2">
          <Button asChild>
            <Link to="/">홈으로</Link>
          </Button>
          <Button variant="secondary" onClick={() => window.location.reload()}>
            다시 불러오기
          </Button>
        </div>
      </div>
    </div>
  )
}
