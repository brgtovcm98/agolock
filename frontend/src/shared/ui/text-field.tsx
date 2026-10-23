import { useId, type ComponentProps } from 'react'
import { cn } from '@/shared/lib/cn'

type TextFieldProps = Omit<ComponentProps<'input'>, 'id'> & {
  id?: string
  label: string
  error?: string
  hint?: string
}

export function TextField({ id, label, error, hint, className, ...props }: TextFieldProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const errorId = `${inputId}-error`
  const hintId = `${inputId}-hint`
  const describedBy = [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(' ')

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={inputId} className="text-xs font-black">
        {label}
      </label>
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={describedBy || undefined}
        className={cn(
          'border-border bg-card text-foreground placeholder:text-foreground/40 focus-visible:ring-foreground focus-visible:ring-offset-background h-12 w-full border-[2.5px] px-3 text-sm font-medium outline-none focus-visible:ring-2 focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50',
          error && 'border-danger',
          className,
        )}
        {...props}
      />
      {hint && !error ? (
        <p id={hintId} className="text-foreground/60 text-[11px]">
          {hint}
        </p>
      ) : null}
      {error ? (
        <p id={errorId} role="alert" className="text-danger text-xs font-bold">
          {error}
        </p>
      ) : null}
    </div>
  )
}
