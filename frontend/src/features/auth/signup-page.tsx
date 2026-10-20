import { zodResolver } from '@hookform/resolvers/zod'
import { useMemo, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { useAuth } from '@/features/auth/auth-provider'
import { AuthLayout } from '@/features/auth/auth-layout'
import { authErrorMessage } from '@/features/auth/errors'
import { ApiError } from '@/shared/api/client'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { PasswordField } from '@/shared/ui/password-field'
import { TextField } from '@/shared/ui/text-field'

type SignupValues = {
  displayName: string
  email: string
  password: string
  confirmPassword: string
  inviteCode?: string
}

export function SignupPage() {
  const { config, signup } = useAuth()
  const navigate = useNavigate()
  const [serverError, setServerError] = useState<string | null>(null)
  const inviteRequired = config?.inviteRequired ?? true
  const minPassword = config?.passwordMinLength ?? 10

  const schema = useMemo(
    () =>
      z
        .object({
          displayName: z.string().trim().min(1, '표시 이름을 입력해 주세요.').max(80),
          email: z
            .string()
            .trim()
            .min(1, '이메일을 입력해 주세요.')
            .email('이메일 형식이 올바르지 않습니다.'),
          password: z
            .string()
            .min(minPassword, `비밀번호는 ${minPassword}자 이상이어야 합니다.`)
            .max(128),
          confirmPassword: z.string().min(1, '비밀번호를 한 번 더 입력해 주세요.'),
          inviteCode: z.string().optional(),
        })
        .superRefine((values, context) => {
          if (values.password !== values.confirmPassword) {
            context.addIssue({
              code: 'custom',
              path: ['confirmPassword'],
              message: '비밀번호가 서로 다릅니다.',
            })
          }
          if (inviteRequired && !values.inviteCode?.trim()) {
            context.addIssue({
              code: 'custom',
              path: ['inviteCode'],
              message: '초대코드를 입력해 주세요.',
            })
          }
        }),
    [inviteRequired, minPassword],
  )

  const {
    register,
    handleSubmit,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SignupValues>({
    resolver: zodResolver(schema),
    defaultValues: {
      displayName: '',
      email: '',
      password: '',
      confirmPassword: '',
      inviteCode: '',
    },
  })

  async function onSubmit(values: SignupValues) {
    setServerError(null)
    try {
      await signup({
        email: values.email,
        password: values.password,
        displayName: values.displayName,
        inviteCode: inviteRequired ? values.inviteCode : undefined,
      })
      navigate('/', { replace: true })
    } catch (error) {
      if (error instanceof ApiError) {
        if (error.code === 'EMAIL_ALREADY_EXISTS') {
          setError('email', { message: error.message })
          return
        }
        if (error.code === 'INVITE_CODE_INVALID' || error.code === 'INVITE_CODE_REQUIRED') {
          setError('inviteCode', { message: error.message })
          return
        }
        for (const fieldError of error.fieldErrors) {
          if (['displayName', 'email', 'password', 'inviteCode'].includes(fieldError.field)) {
            setError(fieldError.field as keyof SignupValues, { message: fieldError.message })
          }
        }
      }
      setServerError(authErrorMessage(error))
    }
  }

  return (
    <AuthLayout
      eyebrow="SCR-002 · 회원가입"
      title="내 물건을 시작해요"
      description="가입하면 나만의 Space가 만들어지고, Room·Shelf·Box로 물건을 정리할 수 있어요."
      footer={
        <span>
          이미 계정이 있나요?{' '}
          <Link className="font-black underline underline-offset-2" to="/login">
            로그인
          </Link>
        </span>
      }
    >
      <form className="flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
        {serverError ? <Alert variant="error">{serverError}</Alert> : null}
        <TextField
          {...register('displayName')}
          label="표시 이름"
          autoComplete="name"
          placeholder="지민"
          error={errors.displayName?.message}
        />
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
          autoComplete="new-password"
          hint={`${minPassword}자 이상 128자 이하`}
          error={errors.password?.message}
        />
        <PasswordField
          {...register('confirmPassword')}
          label="비밀번호 확인"
          autoComplete="new-password"
          error={errors.confirmPassword?.message}
        />
        {inviteRequired ? (
          <TextField
            {...register('inviteCode')}
            label="초대코드"
            autoComplete="off"
            placeholder="AGO-XXXX-XXXX-XXXX-XXXX-XXXX"
            hint="스테이징 환경은 초대코드가 필요합니다."
            error={errors.inviteCode?.message}
          />
        ) : null}
        <Button type="submit" disabled={isSubmitting} aria-busy={isSubmitting}>
          {isSubmitting ? '가입 중…' : '가입하고 시작하기'}
        </Button>
      </form>
    </AuthLayout>
  )
}
