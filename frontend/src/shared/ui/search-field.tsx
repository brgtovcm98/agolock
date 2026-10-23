import { ArrowUpRight } from 'lucide-react'
import type { FormEvent } from 'react'
import { cn } from '@/shared/lib/cn'

export function SearchField({
  value = '',
  placeholder = '물건 검색',
  label = '물건 검색',
  disabled = false,
  readOnly = false,
  className,
  onChange,
  onSubmit,
}: {
  value?: string
  placeholder?: string
  label?: string
  disabled?: boolean
  readOnly?: boolean
  className?: string
  onChange?: (value: string) => void
  onSubmit?: (value: string) => void
}) {
  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    onSubmit?.(value)
  }

  return (
    <form
      role="search"
      className={cn(
        'border-border bg-card shadow-brutal flex border-[2.5px]',
        'focus-within:shadow-brutal-lg focus-within:outline-foreground focus-within:outline-[3px] focus-within:outline-offset-[3px]',
        disabled && 'opacity-80',
        className,
      )}
      onSubmit={handleSubmit}
    >
      <input
        aria-label={label}
        value={value}
        placeholder={placeholder}
        disabled={disabled}
        readOnly={readOnly}
        onChange={(event) => onChange?.(event.target.value)}
        className="text-foreground placeholder:text-foreground/50 min-w-0 flex-1 bg-transparent px-4 py-3 text-base font-medium outline-none disabled:cursor-not-allowed"
      />
      <button
        type="submit"
        aria-label="검색"
        disabled={disabled}
        className="border-border bg-primary text-on-accent grid w-14 place-items-center border-l-[2.5px] disabled:cursor-not-allowed"
      >
        <ArrowUpRight aria-hidden="true" className="size-5" strokeWidth={2} />
      </button>
    </form>
  )
}
