import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { sharingApi } from '@/features/sharing/api'

export function InvitationTokenPage() {
  const navigate = useNavigate()
  const [token, setToken] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)

  useEffect(() => {
    const fromHash = new URLSearchParams(window.location.hash.replace(/^#/, '')).get('token')
    const stored = sessionStorage.getItem('agolock.invitation.token')
    const next = fromHash ?? stored
    if (fromHash) {
      sessionStorage.setItem('agolock.invitation.token', fromHash)
      window.history.replaceState(null, '', '/invitations')
    }
    setToken(next)
  }, [])

  const run = async (action: 'accept' | 'decline') => {
    if (!token) return
    setBusy(true)
    setError(null)
    setMessage(null)
    try {
      if (action === 'accept') {
        await sharingApi.acceptByToken(token)
        sessionStorage.removeItem('agolock.invitation.token')
        navigate('/sharing', { replace: true })
      } else {
        await sharingApi.declineByToken(token)
        sessionStorage.removeItem('agolock.invitation.token')
        setMessage('초대를 거절했습니다.')
      }
    } catch {
      setError(
        '초대를 처리하지 못했습니다. 초대가 만료되었거나 로그인한 이메일과 다를 수 있습니다.',
      )
    } finally {
      setBusy(false)
    }
  }

  return (
    <Card className="mx-auto max-w-xl">
      <CardBody className="items-start gap-3">
        <h1 className="text-xl font-black">공유 초대</h1>
        {!token ? (
          <Alert variant="error">초대 토큰이 없습니다. 메일의 링크를 다시 확인해 주세요.</Alert>
        ) : null}
        {error ? <Alert variant="error">{error}</Alert> : null}
        {message ? <Alert variant="success">{message}</Alert> : null}
        <p className="text-sm">
          초대를 수락하면 공유받은 Room 또는 Shelf가 공유 목록에 나타납니다.
        </p>
        <div className="flex gap-2">
          <Button type="button" disabled={!token || busy} onClick={() => void run('accept')}>
            초대 수락
          </Button>
          <Button
            type="button"
            variant="secondary"
            disabled={!token || busy}
            onClick={() => void run('decline')}
          >
            거절
          </Button>
        </div>
      </CardBody>
    </Card>
  )
}
