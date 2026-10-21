import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderApp } from '@/test/render-app'

describe('앱 라우팅', () => {
  it('M1 홈을 표시한다', () => {
    renderApp(['/'])

    expect(screen.getByRole('heading', { name: '지민님, 반가워요' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '설정 열기' })).toBeInTheDocument()
    expect(screen.getByText('M1에서 연결된 것')).toBeInTheDocument()
  })

  it('인증되지 않으면 로그인 화면으로 보낸다', () => {
    renderApp(['/'], { authenticated: false })

    expect(screen.getByRole('heading', { name: '다시 만났네요' })).toBeInTheDocument()
    expect(screen.getByLabelText('이메일')).toBeInTheDocument()
    expect(screen.getByLabelText('비밀번호')).toBeInTheDocument()
  })

  it('디자인 미리보기에서 3개 시안을 전환한다', async () => {
    const user = userEvent.setup()
    renderApp(['/design-preview?screen=home&mode=mobile'])

    expect(screen.getByText('E안 디자인 미리보기')).toBeInTheDocument()
    expect(screen.getAllByText('전동 드릴 18V').length).toBeGreaterThan(0)

    await user.click(screen.getByRole('button', { name: /공간 트리/ }))
    expect(screen.getByRole('heading', { name: '공간 지도' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /재고 상세/ }))
    expect(screen.getByText('같은 물건 재고 3개 중 1번')).toBeInTheDocument()
  })

  it('재고 화면의 빈 상태를 안내한다', () => {
    renderApp(['/inventory'])

    expect(screen.getByRole('heading', { name: '재고' })).toBeInTheDocument()
    expect(screen.getByText('아직 Inventory가 없습니다.')).toBeInTheDocument()
  })

  it('공유 화면의 빈 상태를 안내한다', () => {
    renderApp(['/sharing'])

    expect(screen.getByRole('heading', { name: '공유' })).toBeInTheDocument()
    expect(screen.getByText('메일의 초대 링크로 수락해 주세요.')).toBeInTheDocument()
  })

  it('공간 트리의 빈 상태를 안내한다', () => {
    renderApp(['/space'])

    expect(screen.getByRole('heading', { name: '공간 지도' })).toBeInTheDocument()
    expect(screen.getByText('아직 방이 없어요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Room 추가' })).toBeInTheDocument()
  })

  it('잘못된 주소는 404를 표시한다', () => {
    renderApp(['/does-not-exist'])

    expect(screen.getByText('404')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '화면을 찾지 못했어요' })).toBeInTheDocument()
  })
})
