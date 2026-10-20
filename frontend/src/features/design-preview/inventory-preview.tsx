import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardRow } from '@/shared/ui/card'
import { PageTitle, SectionTitle } from '@/shared/ui/typography'
import { StatusTag } from '@/shared/ui/status-tag'
import { Thumbnail } from '@/shared/ui/thumbnail'
import { HeroPlaceholder, InfoTable, MovementTimeline } from './preview-components'
import { drillItem } from './preview-data'
import { PreviewShell } from './preview-shell'

export function InventoryPreview({ mode }: { mode: 'mobile' | 'desktop' }) {
  return (
    <PreviewShell mode={mode} active="inventory" searchValue="전동 드릴">
      {mode === 'desktop' ? <DesktopInventory /> : <MobileInventory />}
    </PreviewShell>
  )
}

function MobileInventory() {
  return (
    <>
      <p className="text-xs font-bold">거실 › 공구함 › 파란 박스</p>
      <HeroPlaceholder />
      <div className="flex flex-col gap-1">
        <PageTitle>전동 드릴 18V</PageTitle>
        <p className="text-xs">같은 물건 재고 3개 중 1번</p>
      </div>
      <InfoTable />
      <div className="grid grid-cols-2 gap-3">
        <Button>이동</Button>
        <Button variant="secondary">편집</Button>
      </div>
      <MovementTimeline />
    </>
  )
}

function DesktopInventory() {
  return (
    <>
      <div className="flex flex-wrap items-center justify-between gap-4">
        <div className="flex flex-col gap-2">
          <p className="text-xs font-bold">재고 › 거실 › 공구함 › 파란 박스</p>
          <div className="flex flex-wrap items-center gap-2">
            <PageTitle className="text-[30px]">전동 드릴 18V</PageTitle>
            <StatusTag status={drillItem.status} />
            <Badge>{drillItem.category}</Badge>
            <Badge>같은 물건 재고 3</Badge>
          </div>
        </div>
      </div>

      <div className="flex flex-wrap items-start gap-6">
        <div className="flex min-w-0 flex-1 flex-col gap-4">
          <HeroPlaceholder desktop />
          <div className="flex gap-3">
            <Thumbnail tone="c1" size="wide" className="max-w-[84px]" />
            <Thumbnail tone="c2" size="wide" className="max-w-[84px]" />
            <Thumbnail tone="c3" size="wide" className="max-w-[84px]" />
          </div>
        </div>

        <div className="flex w-[420px] shrink-0 flex-col gap-4">
          <InfoTable desktop />
          <div className="grid grid-cols-2 gap-3">
            <Button>이동</Button>
            <Button variant="secondary">편집</Button>
          </div>
          <MovementTimeline />
        </div>
      </div>

      <SectionTitle aside={<span className="text-xs font-bold">물품 상세 ›</span>}>
        같은 물건의 다른 재고 · {drillItem.inventories.length - 1}
      </SectionTitle>
      <div className="flex flex-col gap-2">
        {drillItem.inventories.slice(1).map((inventory) => (
          <Card key={inventory.id}>
            <CardRow>
              <Thumbnail tone="c2" size="sm" />
              <div className="min-w-0 flex-1">
                <p className="truncate text-sm font-black">{drillItem.name}</p>
                <p className="text-foreground/70 truncate text-[11px]">{inventory.location}</p>
              </div>
              {inventory.owner ? <Badge variant="info">{inventory.owner} 소유</Badge> : null}
              <StatusTag status={inventory.status} />
            </CardRow>
          </Card>
        ))}
      </div>
    </>
  )
}
