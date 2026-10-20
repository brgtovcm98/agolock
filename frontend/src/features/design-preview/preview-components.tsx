import { Check, ChevronDown, ChevronRight, Box, Layers, Package } from 'lucide-react'
import type { ReactNode } from 'react'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardBody, CardRow } from '@/shared/ui/card'
import { SectionTitle } from '@/shared/ui/typography'
import { StatusTag } from '@/shared/ui/status-tag'
import { Thumbnail } from '@/shared/ui/thumbnail'
import { cn } from '@/shared/lib/cn'
import {
  drillInventory,
  drillItem,
  filterGroups,
  formatWon,
  movementHistory,
  recentSearches,
  receivedShares,
  type PreviewItem,
  type SpaceNode,
} from './preview-data'

export function Chip({
  children,
  active = false,
  onClick,
}: {
  children: ReactNode
  active?: boolean
  onClick?: () => void
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      className={cn(
        'border-border relative inline-flex h-7 items-center border-2 px-2.5 text-xs font-bold whitespace-nowrap after:absolute after:inset-x-[-2px] after:-inset-y-2 after:content-[""]',
        active ? 'bg-foreground text-background' : 'bg-card text-foreground',
      )}
    >
      {children}
    </button>
  )
}

export function ChipRow({ children }: { children: ReactNode }) {
  return <div className="flex flex-wrap items-center gap-2">{children}</div>
}

export function ItemCard({ item, compact = false }: { item: PreviewItem; compact?: boolean }) {
  const visible = item.inventories.slice(0, compact ? 1 : 2)
  const remaining = Math.max(item.inventories.length - visible.length, 0)

  return (
    <Card className="overflow-hidden">
      <CardRow className="items-start">
        <Thumbnail tone={item.tone} />
        <div className="flex min-w-0 flex-col gap-1">
          <h3 className="truncate text-base font-black">{item.name}</h3>
          <p className="truncate text-xs">{item.location}</p>
          <StatusTag status={item.status} />
        </div>
      </CardRow>

      {visible.length > 0 ? (
        <div className="border-border bg-sunken border-t-[2.5px]">
          {visible.map((inventory, index) => (
            <div
              key={inventory.id}
              className={cn(
                'flex items-center justify-between gap-2 px-3 py-2 text-xs',
                index < visible.length - 1 && 'border-border border-b-[1.5px] border-dashed',
              )}
            >
              <div className="flex min-w-0 flex-wrap items-center gap-2">
                <span className="truncate">{inventory.location}</span>
                {inventory.owner ? <Badge variant="info">{inventory.owner} 소유</Badge> : null}
              </div>
              <StatusTag status={inventory.status} />
            </div>
          ))}
        </div>
      ) : null}

      {remaining > 0 ? (
        <button
          type="button"
          className="border-border bg-accent text-on-accent w-full border-t-[2.5px] py-2 text-xs font-black"
        >
          + 더 보기 ({remaining})
        </button>
      ) : null}
    </Card>
  )
}

export function FilterPanel() {
  return (
    <Card>
      <CardBody className="gap-3">
        <SectionTitle aside={<span className="text-xs font-bold">초기화</span>}>필터</SectionTitle>
        {filterGroups.map((group) => (
          <fieldset key={group.title} className="flex flex-col gap-1">
            <legend className="mb-1 text-xs font-black">{group.title}</legend>
            {group.items.map((item) => (
              <label key={item.label} className="flex h-8 items-center gap-3 text-[13px] font-bold">
                <span
                  aria-hidden="true"
                  className={cn(
                    'border-border grid size-[18px] place-items-center border-[2.5px]',
                    item.checked ? 'bg-primary text-on-accent' : 'bg-card',
                  )}
                >
                  {item.checked ? <Check className="size-3" strokeWidth={3} /> : null}
                </span>
                <span>{item.label}</span>
                <span className="text-foreground/70 ml-auto font-medium">{item.count}</span>
              </label>
            ))}
          </fieldset>
        ))}
      </CardBody>
    </Card>
  )
}

export function RecentSearches({ mobile = false }: { mobile?: boolean }) {
  return (
    <div className="flex flex-col gap-3">
      <SectionTitle aside={<span className="text-xs font-bold">지우기</span>}>
        최근 검색
      </SectionTitle>
      <ChipRow>
        {recentSearches.map((search) => (
          <Chip key={search}>{search}</Chip>
        ))}
      </ChipRow>
      {mobile ? (
        <p className="text-foreground/60 text-[11px]">최근 검색은 M6에서 서버에 저장됩니다.</p>
      ) : null}
    </div>
  )
}

export function ReceivedShareCard() {
  const share = receivedShares[0]
  return (
    <Card>
      <CardBody className="gap-2">
        <SectionTitle aside="1">받은 공유</SectionTitle>
        <p className="text-sm font-black">{share.name}</p>
        <p className="text-foreground/70 text-xs">
          {share.permission} · {share.expires}
        </p>
        <div className="mt-1 flex items-center gap-2">
          <Badge variant="sto">{share.status}</Badge>
          <Button size="sm" variant="secondary">
            수락
          </Button>
        </div>
      </CardBody>
    </Card>
  )
}

export function TreeNode({ node, depth = 0 }: { node: SpaceNode; depth?: number }) {
  const isRoom = node.tone === 'room'
  const isShared = node.tone === 'shared'
  const isShelf = node.tone === 'shelf'
  const Icon = isShelf ? Layers : isRoom || isShared ? Box : Package
  const hasChildren = 'children' in node && Array.isArray(node.children) && node.children.length > 0
  const badge = 'badge' in node ? node.badge : undefined

  return (
    <div
      className={cn(
        'flex flex-col gap-2',
        depth > 0 && 'border-border ml-[22px] border-l-[2.5px] pl-[14px]',
      )}
    >
      <div
        className={cn(
          'border-border flex items-center justify-between gap-3 border-[2.5px] px-3 py-2.5 font-black',
          isRoom && 'bg-card',
          isShelf && 'bg-yellow-soft',
          node.tone === 'box' && 'bg-blue-soft',
          isShared && 'bg-blue-soft',
          'selected' in node && node.selected && 'bg-primary text-on-accent',
        )}
      >
        <div className="flex min-w-0 flex-col gap-1">
          <div className="flex flex-wrap items-center gap-2">
            <Icon aria-hidden="true" className="size-4 shrink-0" strokeWidth={1.8} />
            <span className="truncate text-sm">{node.name}</span>
            {badge ? <Badge variant="info">{badge}</Badge> : null}
          </div>
          <span className="text-[11px] font-medium">{node.meta}</span>
        </div>
        {hasChildren ? (
          <ChevronDown aria-hidden="true" className="size-4 shrink-0" />
        ) : (
          <ChevronRight aria-hidden="true" className="size-4 shrink-0" />
        )}
      </div>
      {hasChildren ? (
        <div className="flex flex-col gap-2">
          {node.children?.map((child) => (
            <TreeNode key={child.name} node={child} depth={depth + 1} />
          ))}
        </div>
      ) : null}
    </div>
  )
}

export function HeroPlaceholder({ desktop = false }: { desktop?: boolean }) {
  return (
    <div
      className={cn(
        'hero-stripes border-border text-foreground shadow-brutal-lg relative grid place-items-center border-[2.5px]',
        desktop ? 'h-[380px]' : 'h-[230px]',
      )}
      role="img"
      aria-label="전동 드릴 이미지 자리"
    >
      <StatusTag status={drillItem.status} className="absolute top-3 left-3" />
      <Box
        aria-hidden="true"
        strokeWidth={1.2}
        className={desktop ? 'size-[150px]' : 'size-[110px]'}
      />
    </div>
  )
}

export function InfoTable({ desktop = false }: { desktop?: boolean }) {
  const rows = [
    { label: '이름', value: drillItem.name, override: false },
    {
      label: '가격',
      value: formatWon(drillItem.price),
      override: drillItem.overrides.includes('price'),
    },
    { label: '카테고리', value: drillItem.category, override: false },
    {
      label: '설명',
      value: drillItem.description,
      override: drillItem.overrides.includes('description'),
    },
    { label: '위치', value: drillItem.location, override: false },
    ...(desktop ? [{ label: '내 권한', value: '소유자', override: false }] : []),
  ]

  return (
    <table className="border-border bg-card w-full border-collapse border-[2.5px] text-[13px]">
      <tbody>
        {rows.map((row) => (
          <tr key={row.label} className="border-border border-b-[1.5px] last:border-b-0">
            <th className="border-border bg-accent text-on-accent w-[84px] border-r-[2.5px] px-3 py-3 text-left text-[11px] font-black">
              {row.label}
            </th>
            <td className="px-3 py-3 font-medium">
              <span>{row.value}</span>
              {row.override ? (
                <Badge variant="override" className="ml-2">
                  재고값
                </Badge>
              ) : null}
            </td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

export function MovementTimeline() {
  return (
    <div className="flex flex-col gap-3">
      <SectionTitle aside="3">이동 이력</SectionTitle>
      <ol className="border-border ml-2 flex flex-col gap-3 border-l-[2.5px] pl-4 text-xs">
        {movementHistory.map((entry) => (
          <li key={entry.date} className="relative">
            <span
              aria-hidden="true"
              className="border-border bg-primary absolute top-0.5 -left-[23px] size-2.5 border-2"
            />
            {entry.date} · {entry.text}
          </li>
        ))}
      </ol>
    </div>
  )
}

export function DrillSummaryCard() {
  return (
    <Card>
      <CardRow className="items-start">
        <Thumbnail tone="c2" size="sm" />
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-black">{drillItem.name}</p>
          <p className="text-foreground/70 truncate text-[11px]">{drillInventory.location}</p>
        </div>
        <StatusTag status={drillInventory.status} />
      </CardRow>
    </Card>
  )
}
