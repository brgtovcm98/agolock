import { zodResolver } from '@hookform/resolvers/zod'
import { useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link } from 'react-router-dom'
import { z } from 'zod'
import { authApi } from '@/shared/api/auth'
import { authErrorMessage } from '@/features/auth/errors'
import { AuthLayout } from '@/features/auth/auth-layout'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { TextField } from '@/shared/ui/text-field'

const resetSchema = z.object({
  email: z
    .string()
    .trim()
    .min(1, '이메일을 입력해 주세요.')
    .email('이메일 형식이 올바르지 않습니다.'),
})

type ResetValues = z.infer<typeof resetSchema>

export function ResetPasswordPage() {
  const [serverError, setServerError] = useState<string | null>(null)
  const [submitted, setSubmitted] = useState(false)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<ResetValues>({
    resolver: zodResolver(resetSchema),
    defaultValues: { email: '' },
  })

  async function onSubmit(values: ResetValues) {
    setServerError(null)
    try {
      await authApi.requestPasswordReset(values.email)
      setSubmitted(true)
    } catch (error) {
      setServerError(authErrorMessage(error))
    }
  }

  return (
    <AuthLayout
      eyebrow="SCR-001 · 비밀번호 재설정"
      title="비밀번호를 잊었어요"
      description="가입한 이메일을 입력하면 재설정 링크를 보내 드려요. 보안을 위해 가입 여부와 관계없이 같은 안내를 보여 줍니다."
      footer={
        <Link className="font-black underline underline-offset-2" to="/login">
          로그인으로 돌아가기
        </Link>
      }
    >
      {submitted ? (
        <div className="flex flex-col gap-4">
          <Alert variant="success">
            메일을 보냈어요. 받은 편지함에서 재설정 링크를 열어 주세요. 링크는 30분 동안 유효합니다.
          </Alert>
          <Button asChild variant="secondary">
            <Link to="/login">로그인으로</Link>
          </Button>
        </div>
      ) : (
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
          <Button type="submit" disabled={isSubmitting} aria-busy={isSubmitting}>
            {isSubmitting ? '보내는 중…' : '재설정 링크 받기'}
          </Button>
        </form>
      )}
    </AuthLayout>
  )
}
