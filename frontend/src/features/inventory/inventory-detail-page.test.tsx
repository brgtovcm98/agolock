import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { inventoryApi } from './api'
import {
  testBox,
  testCategory,
  testInventory,
  testRoom,
  testShelf,
} from '@/test/inventory-fixtures'
import { renderApp } from '@/test/render-app'

const inventory = testInventory()

function renderDetail() {
  return renderApp(['/inventory/v1'], {
    seeds: [
      [['inventory', 'detail', 'v1'], inventory],
      [['category', 'list', 'i1'], { items: [testCategory] }],
      [['hierarchy', 'room', 'r1'], testRoom()],
      [['hierarchy', 'shelf', 's1'], testShelf()],
      [['hierarchy', 'box', 'b1'], testBox()],
      [['hierarchy', 'rooms', 'picker'], { items: [testRoom()] }],
      [['hierarchy', 'shelves', 'picker', 'owned', 'r1'], { items: [testShelf()] }],
      [['hierarchy', 'boxes', 'picker', 'owned', 's1'], { items: [testBox()] }],
      [['images', 'inventory', 'v1'], { items: [] }],
      [
        ['inventory', 'movements', 'v1'],
        [
          {
            id: 'm1',
            inventoryId: 'v1',
            from: {
              type: 'ROOM',
              roomId: 'r1',
              shelfId: null,
              boxId: null,
              name: '거실',
            },
            to: {
              type: 'BOX',
              roomId: 'r1',
              shelfId: 's1',
              boxId: 'b1',
              name: '파란 박스',
            },
            movedAt: '2026-10-02T00:00:00Z',
          },
        ],
      ],
    ],
  })
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('재고 상세', () => {
  it('재고 정보와 위치 경로를 보여준다', () => {
    renderDetail()

    expect(screen.getByRole('heading', { name: '전동 드릴 18V' })).toBeInTheDocument()
    expect(screen.getAllByText('거실 › 공구함 › 파란 박스').length).toBeGreaterThan(0)
    expect(screen.getByText('이동 이력')).toBeInTheDocument()
    expect(screen.getByText('거실 → 파란 박스')).toBeInTheDocument()
    expect(screen.getByText('129,000원')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /물건 상세/ })).toHaveAttribute('href', '/items/i1')
  })

  it('폐기 상태는 확인 후 저장한다', async () => {
    const user = userEvent.setup()
    const updateSpy = vi
      .spyOn(inventoryApi, 'updateInventory')
      .mockResolvedValue(testInventory({ status: 'DISPOSED', version: 3 }))
    vi.spyOn(inventoryApi, 'inventory').mockResolvedValue(
      testInventory({ status: 'DISPOSED', version: 3 }),
    )
    renderDetail()

    await user.selectOptions(screen.getByLabelText('재고 상태'), 'DISPOSED')

    expect(screen.getByRole('heading', { name: '폐기로 바꿀까요?' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '폐기로 변경' }))

    await waitFor(() =>
      expect(updateSpy).toHaveBeenCalledWith('v1', {
        version: 2,
        status: 'DISPOSED',
        confirmDisposal: true,
      }),
    )
    expect(await screen.findByText('상태를 바꿨어요.')).toBeInTheDocument()
  })

  it('위치 이동 대화상자를 연다', async () => {
    const user = userEvent.setup()
    renderDetail()

    await user.click(screen.getByRole('button', { name: '위치 이동' }))

    expect(screen.getByRole('heading', { name: '위치 이동' })).toBeInTheDocument()
    expect(screen.getByRole('combobox', { name: '대상 위치' })).toBeInTheDocument()
  })
})
