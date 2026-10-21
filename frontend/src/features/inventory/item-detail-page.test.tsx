import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { testCategory, testInventory, testItem, testRoom } from '@/test/inventory-fixtures'
import { renderApp } from '@/test/render-app'

describe('물건 상세', () => {
  it('연결된 재고와 재고 추가 진입을 보여준다', async () => {
    const user = userEvent.setup()
    renderApp(['/items/i1'], {
      seeds: [
        [['item', 'detail', 'i1'], testItem()],
        [['item', 'inventories', 'i1'], { items: [testInventory()], nextCursor: null }],
        [['category', 'list'], { items: [testCategory] }],
        [['images', 'item', 'i1'], { items: [] }],
        [['hierarchy', 'rooms', 'picker'], { items: [testRoom()] }],
        [['hierarchy', 'shelves', 'picker', 'owned', 'r1'], { items: [] }],
        [['hierarchy', 'boxes', 'picker', 'owned', 's1'], { items: [] }],
      ],
    })

    expect(screen.getByRole('heading', { name: '전동 드릴 18V' })).toBeInTheDocument()
    expect(screen.getByText('연결된 재고')).toBeInTheDocument()
    expect(screen.getByText('같은 물건의 재고 1개 · 129,000원')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '사진 추가' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: '재고 추가' }))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('heading', { name: '물건 등록' })).toBeInTheDocument()
    expect(within(dialog).getByRole('tab', { name: '이미 있는 물건' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
  })
})
