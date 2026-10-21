import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { testBox, testInventory, testRoom, testShelf } from '@/test/inventory-fixtures'
import { renderApp } from '@/test/render-app'

describe('Box 상세', () => {
  it('직접 위치 재고와 이 위치에 등록을 연결한다', async () => {
    const user = userEvent.setup()
    renderApp(['/space/boxes/b1'], {
      seeds: [
        [['hierarchy', 'box', 'b1'], testBox()],
        [['hierarchy', 'shelf', 's1'], testShelf()],
        [['inventory', 'location-pool'], { items: [testInventory()], nextCursor: null }],
        [['hierarchy', 'rooms', 'picker'], { items: [testRoom()] }],
        [['hierarchy', 'shelves', 'picker', 'r1'], { items: [testShelf()] }],
        [['hierarchy', 'boxes', 'picker', 's1'], { items: [testBox()] }],
      ],
    })

    expect(screen.getByRole('heading', { name: '파란 박스' })).toBeInTheDocument()
    expect(screen.getByText('직접 위치 재고')).toBeInTheDocument()
    expect(screen.getByText('전동 드릴 18V')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /이 위치에 등록/ }))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('heading', { name: '물건 등록' })).toBeInTheDocument()
    expect(await within(dialog).findByDisplayValue('거실')).toBeInTheDocument()
    expect(within(dialog).getAllByText('거실').length).toBeGreaterThan(0)
    expect(within(dialog).getAllByText(/공구함/).length).toBeGreaterThan(0)
  })
})
