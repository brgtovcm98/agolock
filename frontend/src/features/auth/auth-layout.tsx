import type { ReactNode } from 'react'
import { Brand } from '@/shared/ui/brand'
import { Card, CardBody } from '@/shared/ui/card'
import { PageTitle } from '@/shared/ui/typography'
import { ThemeToggle } from '@/shared/ui/theme-toggle'

export function AuthLayout({
  eyebrow,
  title,
  description,
  children,
  footer,
}: {
  eyebrow: string
  title: string
  description: string
  children: ReactNode
  footer?: ReactNode
}) {
  return (
    <div className="bg-background min-h-dvh px-4 py-6 lg:px-8">
      <div className="mx-auto flex w-full max-w-[420px] flex-col gap-4">
        <div className="flex items-center justify-between">
          <Brand />
          <ThemeToggle />
        </div>
        <Card>
          <CardBody className="gap-5 p-5 lg:p-6">
            <div className="flex flex-col gap-1.5">
              <p className="label-caps text-foreground/60">{eyebrow}</p>
              <PageTitle className="text-[30px]">{title}</PageTitle>
              <p className="text-foreground/70 text-xs leading-relaxed">{description}</p>
            </div>
            {children}
          </CardBody>
        </Card>
        {footer ? <div className="text-foreground/70 text-center text-xs">{footer}</div> : null}
        <p className="text-foreground/50 text-center text-[11px]">AGO LOCK · 개인 물품 관리</p>
      </div>
    </div>
  )
}
