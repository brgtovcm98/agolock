import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { searchApi } from './api'
import { renderApp } from '@/test/render-app'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('검색', () => {
  it('최근 검색어를 누르면 결과를 불러온다', async () => {
    const user = userEvent.setup()
    const searchSpy = vi.spyOn(searchApi, 'search').mockResolvedValue({
      items: [
        {
          itemId: 'i1',
          itemName: '무선 이어폰',
          matchedInventoryCount: 1,
          inventoryCount: 1,
          hasMoreInventories: false,
          primaryImageId: null,
          effectiveCategory: null,
          inventories: [
            {
              inventoryId: 'v1',
              effectiveName: '무선 이어폰',
              status: 'STORED',
              locationPath: '거실 > 서랍',
              shared: false,
              ownerName: null,
            },
          ],
        },
      ],
      nextCursor: null,
    })
    renderApp(['/search'], {
      seeds: [
        [
          ['search', 'recent'],
          {
            items: [
              {
                id: 'r1',
                query: '이어폰',
                lastSearchedAt: '2026-10-22T00:00:00Z',
              },
            ],
          },
        ],
        [['search', 'frequent'], { items: [] }],
      ],
    })

    expect(screen.getByText('최근 검색')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '이어폰' }))

    expect(searchSpy).toHaveBeenCalled()
    expect(await screen.findByText('“이어폰” 검색 결과')).toBeInTheDocument()
    expect((await screen.findAllByText('무선 이어폰')).length).toBeGreaterThan(0)
  })
})
