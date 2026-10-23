import { Eye, EyeOff } from 'lucide-react'
import { useId, useState, type ComponentProps } from 'react'
import { cn } from '@/shared/lib/cn'

type PasswordFieldProps = Omit<ComponentProps<'input'>, 'id' | 'type'> & {
  id?: string
  label: string
  error?: string
  hint?: string
}

export function PasswordField({ id, label, error, hint, className, ...props }: PasswordFieldProps) {
  const [visible, setVisible] = useState(false)
  const generatedId = useId()
  const inputId = id ?? generatedId
  const errorId = `${inputId}-error`
  const hintId = `${inputId}-hint`
  const describedBy = [error ? errorId : null, hint ? hintId : null].filter(Boolean).join(' ')

  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={inputId} className="text-xs font-black">
        {label}
      </label>
      <div className="border-border bg-card focus-within:ring-foreground focus-within:ring-offset-background flex border-[2.5px] focus-within:ring-2 focus-within:ring-offset-2">
        <input
          id={inputId}
          type={visible ? 'text' : 'password'}
          aria-invalid={error ? true : undefined}
          aria-describedby={describedBy || undefined}
          className={cn(
            'text-foreground placeholder:text-foreground/40 h-12 min-w-0 flex-1 bg-transparent px-3 text-sm font-medium outline-none disabled:cursor-not-allowed disabled:opacity-50',
            className,
          )}
          {...props}
        />
        <button
          type="button"
          onClick={() => setVisible((value) => !value)}
          aria-label={visible ? '비밀번호 숨기기' : '비밀번호 표시'}
          aria-pressed={visible}
          className="border-border text-foreground hover:bg-sunken grid w-12 shrink-0 place-items-center border-l-[2.5px]"
        >
          {visible ? (
            <EyeOff aria-hidden="true" className="size-4" strokeWidth={1.8} />
          ) : (
            <Eye aria-hidden="true" className="size-4" strokeWidth={1.8} />
          )}
        </button>
      </div>
      {hint && !error ? (
        <p id={hintId} className="text-foreground/60 text-xs">
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
