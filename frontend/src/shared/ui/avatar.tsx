import { cn } from '@/shared/lib/cn'

export function Avatar({ name = '지', className }: { name?: string; className?: string }) {
  return (
    <span
      className={cn(
        'border-border bg-accent text-on-accent inline-grid size-[34px] shrink-0 place-items-center border-[2.5px] text-sm font-black',
        className,
      )}
      aria-label={`${name} 사용자`}
      title={`${name} 사용자`}
    >
      {name.slice(0, 1)}
    </span>
  )
}
