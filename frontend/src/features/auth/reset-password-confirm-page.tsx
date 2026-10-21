import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useSearchParams } from 'react-router-dom'
import { z } from 'zod'
import { authApi } from '@/shared/api/auth'
import { ApiError } from '@/shared/api/client'
import { authErrorMessage } from '@/features/auth/errors'
import { AuthLayout } from '@/features/auth/auth-layout'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { PasswordField } from '@/shared/ui/password-field'

const schema = z
  .object({
    newPassword: z.string().min(10, '비밀번호는 10자 이상이어야 합니다.').max(128),
    confirmPassword: z.string().min(1, '비밀번호를 한 번 더 입력해 주세요.'),
  })
  .superRefine((values, context) => {
    if (values.newPassword !== values.confirmPassword) {
      context.addIssue({
        code: 'custom',
        path: ['confirmPassword'],
        message: '비밀번호가 서로 다릅니다.',
      })
    }
  })

type Values = z.infer<typeof schema>

export function ResetPasswordConfirmPage() {
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token')
  const [serverError, setServerError] = useState<string | null>(null)
  const [done, setDone] = useState(false)
  const [tokenInvalid, setTokenInvalid] = useState(false)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Values>({
    resolver: zodResolver(schema),
    defaultValues: { newPassword: '', confirmPassword: '' },
  })

  if (!token || tokenInvalid) {
    return (
      <AuthLayout
        eyebrow="비밀번호 재설정"
        title="링크가 올바르지 않아요"
        description="재설정 링크에 토큰이 없습니다. 메일을 다시 요청해 주세요."
      >
        <Button asChild>
          <Link to="/reset-password">재설정 링크 다시 받기</Link>
        </Button>
      </AuthLayout>
    )
  }

  async function onSubmit(values: Values) {
    if (!token) return
    setServerError(null)
    try {
      await authApi.confirmPasswordReset(token, values.newPassword)
      setDone(true)
    } catch (error) {
      if (error instanceof ApiError && error.code === 'PASSWORD_RESET_TOKEN_INVALID') {
        setTokenInvalid(true)
        return
      }
      setServerError(authErrorMessage(error))
    }
  }

  return (
    <AuthLayout
      eyebrow="비밀번호 재설정"
      title={done ? '비밀번호를 바꿨어요' : '새 비밀번호를 정해요'}
      description={
        done
          ? '새 비밀번호로 로그인할 수 있습니다. 기존 로그인 세션은 모두 종료했습니다.'
          : '10자 이상 128자 이하로 새 비밀번호를 입력해 주세요.'
      }
    >
      {done ? (
        <Button asChild>
          <Link to="/login">로그인으로</Link>
        </Button>
      ) : (
        <form className="flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
          {serverError ? <Alert variant="error">{serverError}</Alert> : null}
          <PasswordField
            {...register('newPassword')}
            label="새 비밀번호"
            autoComplete="new-password"
            error={errors.newPassword?.message}
          />
          <PasswordField
            {...register('confirmPassword')}
            label="새 비밀번호 확인"
            autoComplete="new-password"
            error={errors.confirmPassword?.message}
          />
          <Button type="submit" disabled={isSubmitting} aria-busy={isSubmitting}>
            {isSubmitting ? '변경 중…' : '비밀번호 변경'}
          </Button>
        </form>
      )}
    </AuthLayout>
  )
}
