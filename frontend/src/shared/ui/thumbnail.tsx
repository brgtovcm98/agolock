import { Box } from 'lucide-react'
import { cn } from '@/shared/lib/cn'

export type ThumbnailTone = 'c1' | 'c2' | 'c3' | 'c4'

const toneClasses: Record<ThumbnailTone, string> = {
  c1: 'bg-accent',
  c2: 'bg-[#8ec5ff]',
  c3: 'bg-[#ff9d80]',
  c4: 'bg-[#9be3b4]',
}

const sizeClasses = {
  sm: 'size-12',
  md: 'size-16',
  lg: 'size-24',
  wide: 'h-20 w-full',
} as const

export function Thumbnail({
  tone = 'c1',
  size = 'md',
  className,
  label,
}: {
  tone?: ThumbnailTone
  size?: keyof typeof sizeClasses
  className?: string
  label?: string
}) {
  return (
    <span
      className={cn(
        'border-border grid shrink-0 place-items-center border-[2.5px] text-[#111111]',
        toneClasses[tone],
        sizeClasses[size],
        className,
      )}
      aria-label={label ?? '물건 이미지 자리'}
      role="img"
    >
      <Box aria-hidden="true" strokeWidth={1.5} className="size-[56%]" />
    </span>
  )
}
