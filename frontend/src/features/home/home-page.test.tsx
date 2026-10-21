import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { testInventory, testItem } from '@/test/inventory-fixtures'
import { renderApp } from '@/test/render-app'

describe('홈', () => {
  it('최근 추가와 검색 결과를 보여준다', async () => {
    const user = userEvent.setup()
    renderApp(['/'], {
      seeds: [
        [['inventory', 'home', 'recent'], { items: [testInventory()], nextCursor: null }],
        [['item', 'search', '드릴'], { items: [testItem()], nextCursor: null }],
      ],
    })

    expect(screen.getByText('최근 추가')).toBeInTheDocument()
    expect(screen.getByText('전동 드릴 18V')).toBeInTheDocument()

    await user.type(screen.getByLabelText('물건 검색'), '드릴{Enter}')

    expect(await screen.findByText('“드릴” 검색 결과')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /전동 드릴 18V/ })).toHaveAttribute('href', '/items/i1')
  })
})
