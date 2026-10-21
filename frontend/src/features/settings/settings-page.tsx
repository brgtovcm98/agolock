import { zodResolver } from '@hookform/resolvers/zod'
import { useEffect, useState } from 'react'
import { useForm } from 'react-hook-form'
import { Link, useNavigate } from 'react-router-dom'
import { z } from 'zod'
import { useAuth } from '@/features/auth/auth-provider'
import { authErrorMessage } from '@/features/auth/errors'
import { ApiError } from '@/shared/api/client'
import { useTheme, type ThemeMode } from '@/shared/theme/theme-provider'
import { Alert } from '@/shared/ui/alert'
import { Avatar } from '@/shared/ui/avatar'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { PageTitle, MetaText, SectionTitle } from '@/shared/ui/typography'
import { TextField } from '@/shared/ui/text-field'

const settingsSchema = z.object({
  displayName: z.string().trim().min(1, '표시 이름을 입력해 주세요.').max(80),
  spaceName: z.string().trim().min(1, 'Space 이름을 입력해 주세요.').max(120),
})

type SettingsValues = z.infer<typeof settingsSchema>

const themeOptions: Array<{ value: ThemeMode; label: string; description: string }> = [
  { value: 'system', label: '시스템', description: '기기 설정을 따릅니다.' },
  { value: 'light', label: '라이트', description: '밝은 크림 배경입니다.' },
  { value: 'dark', label: '다크', description: '어두운 배경과 크림 테두리입니다.' },
]

export function SettingsPage() {
  const { user, space, updateProfile, updateSpace, logout } = useAuth()
  const { mode, setMode } = useTheme()
  const navigate = useNavigate()
  const [serverError, setServerError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)
  const [loggingOut, setLoggingOut] = useState(false)
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<SettingsValues>({
    resolver: zodResolver(settingsSchema),
    defaultValues: {
      displayName: user?.displayName ?? '',
      spaceName: space?.name ?? '',
    },
  })

  useEffect(() => {
    reset({
      displayName: user?.displayName ?? '',
      spaceName: space?.name ?? '',
    })
  }, [reset, space?.name, user?.displayName])

  async function onSubmit(values: SettingsValues) {
    setServerError(null)
    setSaved(false)
    try {
      await updateProfile(values.displayName)
      await updateSpace(values.spaceName)
      setSaved(true)
    } catch (error) {
      if (error instanceof ApiError) {
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field === 'displayName' || fieldError.field === 'name') {
            setError(fieldError.field === 'name' ? 'spaceName' : 'displayName', {
              message: fieldError.message,
            })
          }
        }
      }
      setServerError(authErrorMessage(error))
    }
  }

  async function onLogout() {
    setLoggingOut(true)
    try {
      await logout()
      navigate('/login', { replace: true })
    } catch (error) {
      setServerError(authErrorMessage(error))
    } finally {
      setLoggingOut(false)
    }
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-col gap-1">
          <p className="label-caps text-foreground/60">설정</p>
          <PageTitle>설정</PageTitle>
          <MetaText>프로필과 Space 이름, 화면 모드, 카테고리를 관리합니다.</MetaText>
        </div>
        <Badge variant="info">{space?.name ?? '내 Space'}</Badge>
      </div>

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_320px]">
        <div className="flex flex-col gap-4">
          <Card>
            <CardBody className="gap-4">
              <SectionTitle aside={<Badge variant="info">소유자</Badge>}>
                프로필과 Space
              </SectionTitle>
              <div className="flex items-center gap-3">
                <Avatar name={user?.displayName ?? '?'} />
                <div className="min-w-0">
                  <p className="truncate text-sm font-black">{user?.displayName}</p>
                  <p className="text-foreground/60 truncate text-xs">{user?.email}</p>
                </div>
              </div>
              <form className="flex flex-col gap-4" onSubmit={handleSubmit(onSubmit)} noValidate>
                {serverError ? <Alert variant="error">{serverError}</Alert> : null}
                {saved ? <Alert variant="success">변경 내용을 저장했어요.</Alert> : null}
                <TextField
                  {...register('displayName')}
                  label="표시 이름"
                  autoComplete="name"
                  error={errors.displayName?.message}
                />
                <TextField
                  {...register('spaceName')}
                  label="Space 이름"
                  autoComplete="organization"
                  error={errors.spaceName?.message}
                />
                <Button type="submit" disabled={isSubmitting} aria-busy={isSubmitting}>
                  {isSubmitting ? '저장 중…' : '변경 저장'}
                </Button>
              </form>
            </CardBody>
          </Card>

          <Card>
            <CardBody className="gap-3">
              <SectionTitle>카테고리</SectionTitle>
              <p className="text-foreground/70 text-xs">
                물건을 묶어 볼 때 사용할 카테고리를 만들고 정리합니다.
              </p>
              <Button asChild variant="secondary">
                <Link to="/settings/categories">카테고리 관리</Link>
              </Button>
            </CardBody>
          </Card>

          <Card>
            <CardBody className="gap-3">
              <SectionTitle>화면 모드</SectionTitle>
              <fieldset className="flex flex-col gap-2">
                <legend className="sr-only">화면 모드 선택</legend>
                {themeOptions.map((option) => (
                  <label
                    key={option.value}
                    className="border-border bg-card hover:bg-sunken flex cursor-pointer items-center gap-3 border-[2.5px] p-3"
                  >
                    <input
                      type="radio"
                      name="theme"
                      value={option.value}
                      checked={mode === option.value}
                      onChange={() => setMode(option.value)}
                      className="accent-primary size-4"
                    />
                    <span className="flex min-w-0 flex-col gap-0.5">
                      <span className="text-sm font-black">{option.label}</span>
                      <span className="text-foreground/60 text-xs">{option.description}</span>
                    </span>
                  </label>
                ))}
              </fieldset>
            </CardBody>
          </Card>
        </div>

        <div className="flex flex-col gap-4">
          <Card>
            <CardBody className="gap-3">
              <SectionTitle>계정</SectionTitle>
              <p className="text-foreground/70 text-xs">
                로그아웃하면 이 계정의 모든 세션이 종료됩니다.
              </p>
              <Button
                type="button"
                variant="secondary"
                onClick={onLogout}
                disabled={loggingOut}
                aria-busy={loggingOut}
              >
                {loggingOut ? '로그아웃 중…' : '로그아웃'}
              </Button>
            </CardBody>
          </Card>

          <Card>
            <CardBody className="gap-2">
              <SectionTitle>앱 정보</SectionTitle>
              <dl className="flex flex-col gap-1 text-xs">
                <div className="flex justify-between gap-2">
                  <dt className="text-foreground/60">버전</dt>
                  <dd className="font-bold">{import.meta.env.VITE_APP_VERSION ?? '0.1.0'}</dd>
                </div>
                <div className="flex justify-between gap-2">
                  <dt className="text-foreground/60">환경</dt>
                  <dd className="font-bold">{import.meta.env.MODE}</dd>
                </div>
                <div className="flex justify-between gap-2">
                  <dt className="text-foreground/60">Space ID</dt>
                  <dd className="max-w-[180px] text-[10px] font-bold break-all">{space?.id}</dd>
                </div>
              </dl>
            </CardBody>
          </Card>
        </div>
      </div>
    </div>
  )
}
