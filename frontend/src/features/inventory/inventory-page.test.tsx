import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { inventoryListKey, testCategory, testInventory } from '@/test/inventory-fixtures'
import { renderApp } from '@/test/render-app'

function page(items: ReturnType<typeof testInventory>[]) {
  return { pages: [{ items, nextCursor: null }], pageParams: [undefined] }
}

describe('재고 목록', () => {
  it('등록된 재고를 위치·상태와 함께 보여주고 상세로 연결한다', () => {
    renderApp(['/inventory'], {
      seeds: [
        [inventoryListKey(), page([testInventory()])],
        [['category', 'list'], { items: [testCategory] }],
      ],
    })

    expect(screen.getByRole('heading', { name: '재고' })).toBeInTheDocument()
    expect(screen.getByText('전동 드릴 18V')).toBeInTheDocument()
    expect(screen.getByText('박스 · 파란 박스')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /전동 드릴 18V/ })).toHaveAttribute(
      'href',
      '/inventory/v1',
    )
  })

  it('재고가 없으면 첫 등록을 안내한다', () => {
    renderApp(['/inventory'], {
      seeds: [[inventoryListKey(), page([])]],
    })

    expect(screen.getByText('아직 등록한 물건이 없어요')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: /첫 물건 등록/ })).toBeInTheDocument()
  })

  it('상태 필터를 선택하면 해당 상태 목록을 조회한다', async () => {
    const user = userEvent.setup()
    renderApp(['/inventory'], {
      seeds: [
        [inventoryListKey(), page([testInventory()])],
        [inventoryListKey(['STORED']), page([testInventory({ status: 'STORED' })])],
      ],
    })

    await user.click(screen.getByRole('button', { name: '보관' }))

    expect(screen.getByRole('button', { name: '보관' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('전동 드릴 18V')).toBeInTheDocument()
  })
})
