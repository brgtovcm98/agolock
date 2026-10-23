import { Brand } from '@/shared/ui/brand'

export function LoadingScreen({ label = '불러오는 중…' }: { label?: string }) {
  return (
    <div className="bg-background grid min-h-dvh place-items-center px-4 py-12">
      <div className="border-border bg-card shadow-brutal flex w-full max-w-sm flex-col items-center gap-4 border-[2.5px] p-6">
        <Brand />
        <p className="text-sm font-bold" role="status">
          {label}
        </p>
        <div className="border-border bg-sunken h-2 w-full border-2">
          <div className="bg-primary h-full w-1/2 animate-pulse" />
        </div>
      </div>
    </div>
  )
}
