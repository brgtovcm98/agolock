import { Badge } from '@/shared/ui/badge'

export type InventoryStatus = 'IN_USE' | 'STORED' | 'DISPOSED'

const labels: Record<InventoryStatus, string> = {
  IN_USE: '사용중',
  STORED: '보관',
  DISPOSED: '폐기',
}

const variants = {
  IN_USE: 'use',
  STORED: 'sto',
  DISPOSED: 'dis',
} as const

export function StatusTag({ status, className }: { status: InventoryStatus; className?: string }) {
  return (
    <Badge variant={variants[status]} className={className}>
      {labels[status]}
    </Badge>
  )
}

export function statusLabel(status: InventoryStatus) {
  return labels[status]
}
