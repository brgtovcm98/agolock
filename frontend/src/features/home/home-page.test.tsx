import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { searchApi } from '@/features/search/api'
import { testInventory } from '@/test/inventory-fixtures'
import { renderApp } from '@/test/render-app'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('홈', () => {
  it('최근 추가를 보여주고 검색 결과 화면으로 이동한다', async () => {
    const user = userEvent.setup()
    const inventory = testInventory()
    const searchSpy = vi.spyOn(searchApi, 'search').mockResolvedValue({
      items: [
        {
          itemId: 'i1',
          itemName: '전동 드릴 18V',
          matchedInventoryCount: 1,
          inventoryCount: 1,
          hasMoreInventories: false,
          primaryImageId: null,
          effectiveCategory: null,
          inventories: [
            {
              inventoryId: inventory.id,
              effectiveName: inventory.displayName,
              status: inventory.status,
              locationPath: '거실 > 선반',
              shared: false,
              ownerName: null,
            },
          ],
        },
      ],
      nextCursor: null,
    })
    renderApp(['/'], {
      seeds: [[['inventory', 'home', 'recent'], { items: [inventory], nextCursor: null }]],
    })

    expect(screen.getByText('최근 추가')).toBeInTheDocument()
    expect(screen.getByText('전동 드릴 18V')).toBeInTheDocument()

    await user.type(screen.getByLabelText('물건 검색'), '드릴{Enter}')

    expect(await screen.findByRole('heading', { name: '무엇을 찾으세요?' })).toBeInTheDocument()
    expect(await screen.findByText('“드릴” 검색 결과')).toBeInTheDocument()
    expect(searchSpy).toHaveBeenCalled()
    expect(
      screen
        .getAllByRole('link', { name: /전동 드릴 18V/ })
        .some((link) => link.getAttribute('href') === '/items/i1'),
    ).toBe(true)
  })
})
