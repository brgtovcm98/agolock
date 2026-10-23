import type { ReactNode } from 'react'
import { Link } from 'react-router-dom'
import { cn } from '@/shared/lib/cn'
import { MetaText, PageTitle } from '@/shared/ui/typography'

/**
 * 화면 머리말. 뒤로가기, 제목, 설명, 우측 액션의 순서를 통일한다.
 * eyebrow는 제목과 다른 정보일 때만 쓴다.
 */
export function PageHeader({
  title,
  description,
  eyebrow,
  back,
  actions,
  className,
}: {
  title: ReactNode
  description?: ReactNode
  eyebrow?: ReactNode
  back?: { to: string; label: string }
  actions?: ReactNode
  className?: string
}) {
  return (
    <header className={cn('flex flex-wrap items-end justify-between gap-3', className)}>
      <div className="flex min-w-0 flex-col gap-1">
        {back ? (
          <Link to={back.to} className="tap text-xs font-bold underline underline-offset-2">
            ← {back.label}
          </Link>
        ) : null}
        {eyebrow ? <p className="label-caps text-foreground/70">{eyebrow}</p> : null}
        <PageTitle>{title}</PageTitle>
        {description ? <MetaText>{description}</MetaText> : null}
      </div>
      {actions}
    </header>
  )
}
