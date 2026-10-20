import { zodResolver } from '@hookform/resolvers/zod'
import { useForm } from 'react-hook-form'
import { Link, useNavigate, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { useAuth } from '@/features/auth/auth-provider'
import { AuthLayout } from '@/features/auth/auth-layout'
import { authErrorMessage } from '@/features/auth/errors'
import { safeRedirect } from '@/features/auth/protected-route'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { PasswordField } from '@/shared/ui/password-field'
import { TextField } from '@/shared/ui/text-field'
import { useState } from 'react'

const loginSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, '이메일을 입력해 주세요.')
    .email('이메일 형식이 올바르지 않습니다.'),
  password: z.string().min(1, '비밀번호를 입력해 주세요.'),
})

type LoginValues = z.infer<typeof loginSchema>

export function LoginPage() {
  const { login } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const [serverError, setServerError] = useState<string | null>(null)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: '', password: '' },
  })

  async function onSubmit(values: LoginValues) {
    setServerError(null)
    try {
      await login(values)
      navigate(safeRedirect(searchParams.get('redirect')), { replace: true })
    } catch (error) {
      setServerError(authErrorMessage(error))
    }
  }

  return (
    <AuthLayout
      eyebrow="SCR-001 · 로그인"
      title="다시 만났네요"
      description="이메일과 비밀번호로 로그인하면 내 Space의 물건을 이어서 관리할 수 있어요."
      footer={
        <span>
          아직 계정이 없나요?{' '}
          <Link className="font-black underline underline-offset-2" to="/signup">
            회원가입
          </Link>
        </span>
      }
    >
      <form className="flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
        {serverError ? <Alert variant="error">{serverError}</Alert> : null}
        <TextField
          {...register('email')}
          label="이메일"
          type="email"
          autoComplete="email"
          placeholder="you@example.com"
          error={errors.email?.message}
        />
        <PasswordField
          {...register('password')}
          label="비밀번호"
          autoComplete="current-password"
          placeholder="비밀번호"
          error={errors.password?.message}
        />
        <Button type="submit" disabled={isSubmitting} aria-busy={isSubmitting}>
          {isSubmitting ? '로그인 중…' : '로그인'}
        </Button>
        <div className="flex items-center justify-center">
          <Link className="text-xs font-bold underline underline-offset-2" to="/reset-password">
            비밀번호를 잊었어요
          </Link>
        </div>
      </form>
    </AuthLayout>
  )
}
