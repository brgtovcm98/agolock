import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { queryClient } from '@/app/providers'
import { renderApp, testAuthConfig } from '@/test/render-app'

describe('인증 화면', () => {
  afterEach(() => {
    queryClient.clear()
    vi.unstubAllGlobals()
  })

  it('로그인 필수 입력을 검증한다', async () => {
    const user = userEvent.setup()
    renderApp(['/login'], { authenticated: false })

    await user.click(screen.getByRole('button', { name: '로그인' }))

    expect(await screen.findByText('이메일을 입력해 주세요.')).toBeInTheDocument()
    expect(screen.getByText('비밀번호를 입력해 주세요.')).toBeInTheDocument()
  })

  it('초대코드가 필요 없는 환경에서는 초대코드 입력을 숨긴다', () => {
    renderApp(['/signup'], {
      authenticated: false,
      config: { ...testAuthConfig, inviteRequired: false },
    })

    expect(screen.queryByLabelText('초대코드')).not.toBeInTheDocument()
  })

  it('초대코드가 필요한 환경에서는 초대코드 입력을 표시한다', () => {
    renderApp(['/signup'], {
      authenticated: false,
      config: { ...testAuthConfig, inviteRequired: true },
    })

    expect(screen.getByLabelText('초대코드')).toBeInTheDocument()
  })

  it('로그아웃하면 이전 사용자의 계층 캐시를 비운다', async () => {
    const user = userEvent.setup()
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response(null, { status: 204 })))
    renderApp(['/settings'])
    queryClient.setQueryData(['hierarchy', 'rooms', 'tree'], {
      items: [{ id: 'old-room', name: '이전 사용자의 방' }],
      nextCursor: null,
    })

    await user.click(screen.getByRole('button', { name: '로그아웃' }))

    await waitFor(() => {
      expect(queryClient.getQueryData(['hierarchy', 'rooms', 'tree'])).toBeUndefined()
    })
  })

  it('설정 화면에 현재 프로필과 Space가 표시된다', () => {
    renderApp(['/settings'])

    expect(screen.getByRole('heading', { name: '설정' })).toBeInTheDocument()
    expect(screen.getByLabelText('표시 이름')).toHaveValue('지민')
    expect(screen.getByLabelText('공간 이름')).toHaveValue('지민의 Space')
    expect(screen.getByRole('radio', { name: /시스템/ })).toBeChecked()
  })
})
