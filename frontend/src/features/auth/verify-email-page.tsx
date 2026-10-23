import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { authApi } from '@/shared/api/auth'
import { Alert } from '@/shared/ui/alert'
import { Card, CardBody } from '@/shared/ui/card'

export function VerifyEmailPage() {
  const [state, setState] = useState<'loading' | 'success' | 'error'>(() =>
    new URLSearchParams(window.location.hash.replace(/^#/, '')).get('token') ? 'loading' : 'error',
  )

  useEffect(() => {
    const token = new URLSearchParams(window.location.hash.replace(/^#/, '')).get('token')
    if (!token) return
    window.history.replaceState(null, '', '/verify-email')
    void authApi
      .confirmEmailVerification(token)
      .then(() => setState('success'))
      .catch(() => setState('error'))
  }, [])

  return (
    <div className="mx-auto flex min-h-dvh max-w-lg items-center p-4">
      <Card className="w-full">
        <CardBody className="items-start gap-3">
          <h1 className="text-xl font-black">이메일 인증</h1>
          {state === 'loading' ? <p className="text-sm">인증을 확인하는 중…</p> : null}
          {state === 'success' ? (
            <Alert variant="success">이메일 인증이 완료되었습니다.</Alert>
          ) : null}
          {state === 'error' ? (
            <Alert variant="error">인증 링크가 올바르지 않거나 만료되었습니다.</Alert>
          ) : null}
          <Link className="tap text-sm font-black underline underline-offset-2" to="/">
            AGO Lock으로 이동
          </Link>
        </CardBody>
      </Card>
    </div>
  )
}
