import { Badge } from '@/shared/ui/badge'
import { Card, CardRow } from '@/shared/ui/card'
import { PageTitle, SectionTitle } from '@/shared/ui/typography'
import { StatusTag } from '@/shared/ui/status-tag'
import { Thumbnail } from '@/shared/ui/thumbnail'
import { ItemCard, Chip, ChipRow, FilterPanel, ChipRow as Chips } from './preview-components'
import { boxGrid, drillItem, spaceTree } from './preview-data'
import { PreviewMobileHeader, PreviewShell } from './preview-shell'

export function SpacePreview({ mode }: { mode: 'mobile' | 'desktop' }) {
  return (
    <PreviewShell mode={mode} active="space" searchValue="공구함">
      {mode === 'desktop' ? <DesktopSpace /> : <MobileSpace />}
    </PreviewShell>
  )
}

function MobileSpace() {
  return (
    <>
      <PreviewMobileHeader variant="space" />
      <PageTitle>공간 지도</PageTitle>
      <ChipRow>
        <Chip active>내 공간</Chip>
        <Chip>민지 공유 · 편집</Chip>
      </ChipRow>
      <SectionTitle aside={<span className="text-xs font-bold">+ 추가</span>}>
        ROOM / SHELF / BOX
      </SectionTitle>
      <div className="flex flex-col gap-2">
        {spaceTree.map((node) => (
          <TreeNodeList key={node.name} node={node} />
        ))}
      </div>
      <SectionTitle>직접 위치 재고</SectionTitle>
      <ItemCard item={drillItem} compact />
    </>
  )
}

function DesktopSpace() {
  return (
    <>
      <div className="flex flex-wrap items-end justify-between gap-4">
        <div className="flex flex-col gap-1">
          <PageTitle className="text-[30px]">공간 지도</PageTitle>
          <p className="text-xs">내 공간과 공유받은 공간을 함께 봅니다.</p>
        </div>
        <ChipRow>
          <Chip active>내 공간</Chip>
          <Chip>민지 공유 · 편집</Chip>
        </ChipRow>
      </div>

      <div className="flex items-start gap-6">
        <section className="flex w-[420px] shrink-0 flex-col gap-3">
          <SectionTitle aside={<span className="text-xs font-bold">+ 추가</span>}>
            ROOM / SHELF / BOX
          </SectionTitle>
          <div className="flex flex-col gap-2">
            {spaceTree.map((node) => (
              <TreeNodeList key={node.name} node={node} />
            ))}
          </div>
        </section>

        <section className="flex min-w-0 flex-1 flex-col gap-4">
          <p className="text-xs font-bold">거실 › 공구함</p>
          <div className="flex flex-wrap items-center gap-2">
            <h2 className="text-2xl font-black">공구함</h2>
            <Badge variant="info">편집</Badge>
            <Badge>재고 8</Badge>
          </div>
          <SectionTitle>BOX</SectionTitle>
          <div className="grid grid-cols-3 gap-4">
            {boxGrid.map((box) => (
              <Card key={box.name}>
                <CardRow className="items-start">
                  <Thumbnail tone={box.tone} size="wide" />
                </CardRow>
                <div className="border-border flex items-center justify-between gap-2 border-t-[2.5px] px-3 py-2">
                  <span className="text-sm font-black">{box.name}</span>
                  <span className="text-xs">{box.count}</span>
                </div>
              </Card>
            ))}
          </div>
          <SectionTitle>직접 위치 재고</SectionTitle>
          <ItemCard item={drillItem} compact />
        </section>
      </div>
    </>
  )
}

function TreeNodeList({ node }: { node: (typeof spaceTree)[number] }) {
  const children = node.children ?? []
  return (
    <div className="flex flex-col gap-2">
      <div className="border-border bg-card flex items-center justify-between gap-3 border-[2.5px] px-3 py-2.5">
        <div className="flex min-w-0 flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <span aria-hidden="true" className="size-3 shrink-0 border-2 border-current" />
            <span className="truncate text-sm font-black">{node.name}</span>
            {node.badge ? <Badge variant="info">{node.badge}</Badge> : null}
          </div>
          <span className="text-[11px]">{node.meta}</span>
        </div>
        <span aria-hidden="true">{children.length > 0 ? '▾' : '›'}</span>
      </div>
      {children.length > 0 ? (
        <div className="border-border ml-[22px] flex flex-col gap-2 border-l-[2.5px] pl-[14px]">
          {children.map((child) => (
            <div key={child.name} className="flex flex-col gap-2">
              <div className="border-border bg-yellow-soft flex items-center justify-between gap-3 border-[2.5px] px-3 py-2.5">
                <div className="flex min-w-0 flex-col gap-1">
                  <span className="text-sm font-black">{child.name}</span>
                  <span className="text-[11px]">{child.meta}</span>
                </div>
                <span aria-hidden="true">{child.children?.length ? '▾' : '›'}</span>
              </div>
              {child.children?.map((box) => (
                <div
                  key={box.name}
                  className="border-border bg-blue-soft ml-[22px] flex items-center justify-between gap-3 border-[2.5px] px-3 py-2.5"
                >
                  <div className="flex min-w-0 flex-col gap-1">
                    <span className="text-sm font-black">{box.name}</span>
                    <span className="text-[11px]">{box.meta}</span>
                  </div>
                  <StatusTag status="STORED" />
                </div>
              ))}
            </div>
          ))}
        </div>
      ) : null}
    </div>
  )
}

export function SpaceFilterPreview() {
  return (
    <div className="flex flex-col gap-3">
      <FilterPanel />
      <Chips>
        <Chip>필터는 M6</Chip>
      </Chips>
    </div>
  )
}
