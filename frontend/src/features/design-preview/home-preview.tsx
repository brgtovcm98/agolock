import type { ReactNode } from 'react'
import { SearchField } from '@/shared/ui/search-field'
import { PageTitle, MetaText } from '@/shared/ui/typography'
import { Badge } from '@/shared/ui/badge'
import { cn } from '@/shared/lib/cn'
import {
  Chip,
  ChipRow,
  FilterPanel,
  ItemCard,
  RecentSearches,
  ReceivedShareCard,
} from './preview-components'
import { previewItems } from './preview-data'
import { PreviewMobileHeader, PreviewShell } from './preview-shell'

export function HomePreview({ mode }: { mode: 'mobile' | 'desktop' }) {
  return (
    <PreviewShell mode={mode} active="home" searchValue="전동 드릴">
      {mode === 'desktop' ? <DesktopHome /> : <MobileHome />}
    </PreviewShell>
  )
}

function MobileHome() {
  return (
    <>
      <PreviewMobileHeader />
      <div className="flex flex-col gap-1">
        <PageTitle>뭐 찾아요?</PageTitle>
        <MetaText>M0 디자인 미리보기 · 목데이터</MetaText>
      </div>
      <SearchField value="전동 드릴" readOnly />
      <ChipRow>
        <Chip active>전체</Chip>
        <Chip>사진 있음</Chip>
        <Chip>공유받은</Chip>
        <Chip>이미지로</Chip>
      </ChipRow>

      <ResultHeader count={2} />
      <div className="flex flex-col gap-4">
        {previewItems.slice(0, 2).map((item) => (
          <ItemCard key={item.id} item={item} />
        ))}
      </div>

      <RecentSearches mobile />
      <ReceivedShareCard />
    </>
  )
}

function DesktopHome() {
  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <PageTitle className="text-[30px]">“전동 드릴” 검색 결과 2건</PageTitle>
          <MetaText>공유받은 재고를 포함한 결과입니다.</MetaText>
        </div>
        <ChipRow>
          <Chip active>전체</Chip>
          <Chip>사진 있음</Chip>
          <Chip>공유받은</Chip>
          <Chip>이미지로 검색</Chip>
          <Chip>관련도 ▾</Chip>
        </ChipRow>
      </div>

      <div className="flex flex-wrap items-start gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          {previewItems.slice(0, 2).map((item) => (
            <ItemCard key={item.id} item={item} />
          ))}
        </div>
        <div className="flex w-[300px] shrink-0 flex-col gap-4">
          <FilterPanel />
          <RecentSearches />
          <ReceivedShareCard />
        </div>
      </div>
    </>
  )
}

function ResultHeader({ count }: { count: number }) {
  return (
    <div className="flex items-end justify-between gap-3">
      <div className="flex flex-col gap-1">
        <h2 className="border-border border-b-[2.5px] pb-2 text-xs font-black tracking-[0.08em]">
          검색 결과 · {count}
        </h2>
      </div>
      <span className="pb-2 text-xs font-bold">관련도 ▾</span>
    </div>
  )
}

export function PreviewSection({
  children,
  className,
}: {
  children: ReactNode
  className?: string
}) {
  return <section className={cn('flex flex-col gap-3', className)}>{children}</section>
}

export function OwnerBadge() {
  return <Badge variant="info">민지 소유</Badge>
}
