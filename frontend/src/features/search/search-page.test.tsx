import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { searchApi } from './api'
import { renderApp } from '@/test/render-app'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('검색', () => {
  it('검색어 없이 공유 범위와 사진 필터를 적용하고 초기화한다', async () => {
    const user = userEvent.setup()
    const searchSpy = vi
      .spyOn(searchApi, 'search')
      .mockResolvedValue({ items: [], nextCursor: null })
    renderApp(['/search'], {
      seeds: [
        [['search', 'recent'], { items: [] }],
        [['search', 'frequent'], { items: [] }],
      ],
    })
    await user.selectOptions(screen.getByLabelText('검색 범위'), 'SHARED')
    await waitFor(() =>
      expect(searchSpy).toHaveBeenCalledWith(
        '',
        expect.objectContaining({ scope: 'SHARED' }),
        undefined,
      ),
    )
    await user.click(screen.getByLabelText('사진 있음'))
    await waitFor(() =>
      expect(searchSpy).toHaveBeenCalledWith(
        '',
        expect.objectContaining({ hasImage: true }),
        undefined,
      ),
    )
    await user.click(screen.getByRole('button', { name: '검색 초기화' }))
    expect(screen.getByLabelText('검색 범위')).toHaveValue('ALL')
    expect(screen.getByLabelText('사진 있음')).not.toBeChecked()
    expect(screen.queryByText('필터 결과')).not.toBeInTheDocument()
  })

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
