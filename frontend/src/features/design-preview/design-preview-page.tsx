import { Link, useSearchParams } from 'react-router-dom'
import { Monitor, Smartphone } from 'lucide-react'
import { Badge } from '@/shared/ui/badge'
import { Brand } from '@/shared/ui/brand'
import { Button } from '@/shared/ui/button'
import { ThemeToggle } from '@/shared/ui/theme-toggle'
import { HomePreview } from './home-preview'
import { InventoryPreview } from './inventory-preview'
import { SpacePreview } from './space-preview'

type PreviewScreen = 'home' | 'space' | 'inventory'
type PreviewMode = 'mobile' | 'desktop'

const screens: Array<{ id: PreviewScreen; label: string; code: string }> = [
  { id: 'home', label: '홈·검색', code: 'SCR-003' },
  { id: 'space', label: '공간 트리', code: 'SCR-004' },
  { id: 'inventory', label: '재고 상세', code: 'SCR-009' },
]

const tokenSwatches = [
  { name: 'background', className: 'bg-background' },
  { name: 'card', className: 'bg-card' },
  { name: 'primary', className: 'bg-primary' },
  { name: 'accent', className: 'bg-accent' },
  { name: 'success', className: 'bg-success' },
  { name: 'info', className: 'bg-info' },
]

function isScreen(value: string | null): value is PreviewScreen {
  return value === 'home' || value === 'space' || value === 'inventory'
}

export function DesignPreviewPage() {
  const [params, setParams] = useSearchParams()
  const screen: PreviewScreen = isScreen(params.get('screen'))
    ? (params.get('screen') as PreviewScreen)
    : 'home'
  const mode: PreviewMode = params.get('mode') === 'desktop' ? 'desktop' : 'mobile'

  function update(next: { screen?: PreviewScreen; mode?: PreviewMode }) {
    const nextParams = new URLSearchParams(params)
    nextParams.set('screen', next.screen ?? screen)
    nextParams.set('mode', next.mode ?? mode)
    setParams(nextParams, { replace: true })
  }

  return (
    <div className="bg-background min-h-dvh">
      <header className="border-border bg-background/95 sticky top-0 z-30 border-b-[2.5px] backdrop-blur">
        <div className="mx-auto flex w-full max-w-[1440px] flex-wrap items-center gap-3 px-4 py-3 lg:px-8">
          <Brand label="AGOLOCK" />
          <div className="flex flex-col">
            <p className="text-xs font-black">E안 디자인 미리보기</p>
            <p className="text-foreground/70 text-[11px]">목데이터 · 실제 API 연결 전</p>
          </div>
          <div className="ml-auto flex flex-wrap items-center gap-2">
            <Badge variant="sto">DESIGN QA</Badge>
            <Button asChild variant="secondary" size="sm">
              <Link to="/">M0 홈</Link>
            </Button>
            <ThemeToggle className="size-11" />
          </div>
        </div>
      </header>

      <div className="mx-auto flex w-full max-w-[1440px] flex-col gap-4 px-4 py-5 lg:px-8">
        <div className="flex flex-wrap items-center gap-2">
          {screens.map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => update({ screen: item.id })}
              aria-pressed={screen === item.id}
              className={
                screen === item.id
                  ? 'border-border bg-primary text-on-accent shadow-brutal-sm inline-flex h-10 items-center gap-2 border-[2.5px] px-3 text-xs font-black'
                  : 'border-border bg-card inline-flex h-10 items-center gap-2 border-[2.5px] px-3 text-xs font-black'
              }
            >
              {item.label}
              <span className="text-[10px] font-bold opacity-70">{item.code}</span>
            </button>
          ))}

          <div className="ml-auto flex items-center gap-2">
            <button
              type="button"
              onClick={() => update({ mode: 'mobile' })}
              aria-pressed={mode === 'mobile'}
              className={
                mode === 'mobile'
                  ? 'border-border bg-foreground text-background inline-flex h-10 items-center gap-2 border-[2.5px] px-3 text-xs font-black'
                  : 'border-border bg-card inline-flex h-10 items-center gap-2 border-[2.5px] px-3 text-xs font-black'
              }
            >
              <Smartphone aria-hidden="true" className="size-4" />
              모바일 390
            </button>
            <button
              type="button"
              onClick={() => update({ mode: 'desktop' })}
              aria-pressed={mode === 'desktop'}
              className={
                mode === 'desktop'
                  ? 'border-border bg-foreground text-background inline-flex h-10 items-center gap-2 border-[2.5px] px-3 text-xs font-black'
                  : 'border-border bg-card inline-flex h-10 items-center gap-2 border-[2.5px] px-3 text-xs font-black'
              }
            >
              <Monitor aria-hidden="true" className="size-4" />
              데스크톱 1280
            </button>
          </div>
        </div>

        <div className="border-border bg-card flex flex-wrap items-center gap-3 border-[2.5px] px-3 py-2">
          <span className="label-caps">TOKENS</span>
          {tokenSwatches.map((token) => (
            <span
              key={token.name}
              className="inline-flex items-center gap-1.5 text-[10px] font-bold"
            >
              <span
                className={`border-border size-4 border-2 ${token.className}`}
                aria-hidden="true"
              />
              {token.name}
            </span>
          ))}
          <span className="text-foreground/60 ml-auto text-[10px] font-medium">
            디자인 시스템 · 네오 브루탈 · Noto Sans KR
          </span>
        </div>

        <div className="overflow-x-auto pb-6">
          <div className="mx-auto w-max">
            {screen === 'home' ? <HomePreview mode={mode} /> : null}
            {screen === 'space' ? <SpacePreview mode={mode} /> : null}
            {screen === 'inventory' ? <InventoryPreview mode={mode} /> : null}
          </div>
        </div>

        <div className="border-border text-foreground/70 flex flex-wrap items-center justify-between gap-3 border-t-[2.5px] pt-3 text-[11px]">
          <span>시안 E안 · 모바일 라이트/다크, 데스크톱 라이트</span>
          <span>이 화면은 제품 기능이 아니라 시각 회귀 확인용입니다.</span>
        </div>
      </div>
    </div>
  )
}
