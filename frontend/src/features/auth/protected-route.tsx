import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '@/features/auth/auth-provider'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { LoadingScreen } from '@/shared/ui/loading-screen'

export function safeRedirect(value: string | null): string {
  if (!value || !value.startsWith('/') || value.startsWith('//')) return '/'
  return value
}

export function RequireAuth({ children }: { children: ReactNode }) {
  const { isLoading, isAuthenticated, authError, retryAuth } = useAuth()
  const location = useLocation()

  if (isLoading) return <LoadingScreen label="세션을 확인하는 중…" />
  if (authError && !isAuthenticated) {
    return (
      <Card>
        <CardBody className="items-start gap-3">
          <Alert variant="error">세션을 확인하지 못했습니다. 네트워크 상태를 확인해 주세요.</Alert>
          <Button type="button" onClick={retryAuth}>
            다시 시도
          </Button>
        </CardBody>
      </Card>
    )
  }
  if (!isAuthenticated) {
    const redirect = encodeURIComponent(`${location.pathname}${location.search}`)
    return <Navigate to={`/login?redirect=${redirect}`} replace />
  }
  return children
}

export function PublicOnlyRoute({ children }: { children: ReactNode }) {
  const { isLoading, isAuthenticated } = useAuth()

  if (isLoading) return <LoadingScreen label="세션을 확인하는 중…" />
  if (isAuthenticated) return <Navigate to="/" replace />
  return children
}
