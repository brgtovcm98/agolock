import { Link } from 'react-router-dom'
import type { Inventory } from '../api'
import { Badge } from '@/shared/ui/badge'
import { StatusTag } from '@/shared/ui/status-tag'
import { Thumbnail } from '@/shared/ui/thumbnail'
import { locationTypeLabel } from '@/shared/lib/format'

function hasOverride(inventory: Inventory) {
  return Boolean(
    inventory.nameOverride !== null ||
    inventory.descriptionOverride !== null ||
    inventory.categoryIdOverride !== null ||
    inventory.priceAmountOverride !== null,
  )
}

export function InventoryCard({ inventory }: { inventory: Inventory }) {
  return (
    <Link
      to={`/inventory/${inventory.id}`}
      viewTransition
      className="surface hover:bg-sunken hover:shadow-brutal-lg flex items-center gap-3 p-3 transition-[translate,box-shadow,background-color] duration-[var(--motion-press)] ease-[var(--ease-snap)] hover:-translate-y-px active:translate-y-0"
    >
      <Thumbnail tone={toneFor(inventory.id)} size="sm" />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-black">{inventory.displayName}</p>
        <p className="text-foreground/70 truncate text-xs">
          {locationTypeLabel(inventory.location.type)} · {inventory.location.name}
        </p>
      </div>
      <div className="flex shrink-0 flex-col items-end gap-1">
        <StatusTag status={inventory.status} />
        {hasOverride(inventory) ? <Badge variant="override">재고값</Badge> : null}
      </div>
    </Link>
  )
}

function toneFor(id: string) {
  const tones = ['c1', 'c2', 'c3', 'c4'] as const
  const index = [...id].reduce((sum, char) => sum + char.charCodeAt(0), 0) % tones.length
  return tones[index]
}
