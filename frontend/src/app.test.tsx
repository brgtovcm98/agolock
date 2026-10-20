import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { renderApp } from '@/test/render-app'

describe('앱 라우팅', () => {
  it('M0 홈을 표시한다', () => {
    renderApp(['/'])

    expect(screen.getByRole('heading', { name: '기반이 준비됐어요' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: '디자인 미리보기' })).toBeInTheDocument()
    expect(screen.getByText('M0에서 확인한 것')).toBeInTheDocument()
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

  it('M0에 없는 화면은 안내한다', () => {
    renderApp(['/space'])

    expect(screen.getByRole('heading', { name: '공간' })).toBeInTheDocument()
    expect(screen.getByText(/M0에서 셸과 내비게이션만 확인/)).toBeInTheDocument()
  })

  it('잘못된 주소는 404를 표시한다', () => {
    renderApp(['/does-not-exist'])

    expect(screen.getByText('404')).toBeInTheDocument()
    expect(screen.getByRole('heading', { name: '화면을 찾지 못했어요' })).toBeInTheDocument()
  })
})
