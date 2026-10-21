import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { inventoryApi } from './api'
import { ApiError } from '@/shared/api/client'
import { testInventory, testRoom } from '@/test/inventory-fixtures'
import { renderApp } from '@/test/render-app'

function openSheet() {
  const buttons = screen.getAllByRole('button', { name: '물건 등록' })
  return buttons[0]
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('등록 시트', () => {
  it('새 물건과 첫 재고를 한 번에 등록한다', async () => {
    const user = userEvent.setup()
    const createInventorySpy = vi
      .spyOn(inventoryApi, 'createInventory')
      .mockResolvedValue(testInventory())
    vi.spyOn(inventoryApi, 'inventories').mockResolvedValue({ items: [], nextCursor: null })
    vi.spyOn(inventoryApi, 'itemInventories').mockResolvedValue({ items: [], nextCursor: null })

    renderApp(['/'], {
      seeds: [
        [['hierarchy', 'rooms', 'picker'], { items: [testRoom()] }],
        [['hierarchy', 'shelves', 'picker', 'owned', 'r1'], { items: [] }],
        [['category', 'list'], { items: [] }],
      ],
    })

    await user.click(openSheet())
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('물건 이름'), '새 드릴')
    await user.selectOptions(await within(dialog).findByLabelText('방', {}, { timeout: 3000 }), 'r1')
    await user.click(within(dialog).getByRole('button', { name: '등록' }))

    await waitFor(() => expect(createInventorySpy).toHaveBeenCalledTimes(1))
    expect(createInventorySpy).toHaveBeenCalledWith(
      expect.objectContaining({
        newItem: expect.objectContaining({ name: '새 드릴' }),
        roomId: 'r1',
        status: 'STORED',
        idempotencyKey: expect.any(String),
      }),
    )
    expect(await within(dialog).findByText('등록했어요.')).toBeInTheDocument()
    expect(within(dialog).getByRole('button', { name: /계속 등록/ })).toBeInTheDocument()
    expect(within(dialog).getByRole('link', { name: '물건 보기' })).toHaveAttribute(
      'href',
      '/items/i1',
    )
  })

  it('등록이 실패하면 같은 입력으로 다시 시도한다', async () => {
    const user = userEvent.setup()
    const createInventorySpy = vi
      .spyOn(inventoryApi, 'createInventory')
      .mockRejectedValueOnce(
        new ApiError({
          title: '등록 실패',
          status: 500,
          code: 'SERVER_ERROR',
          detail: '등록하지 못했습니다.',
        }),
      )
      .mockResolvedValueOnce(testInventory())
    vi.spyOn(inventoryApi, 'inventories').mockResolvedValue({ items: [], nextCursor: null })
    vi.spyOn(inventoryApi, 'itemInventories').mockResolvedValue({ items: [], nextCursor: null })

    renderApp(['/'], {
      seeds: [
        [['hierarchy', 'rooms', 'picker'], { items: [testRoom()] }],
        [['hierarchy', 'shelves', 'picker', 'owned', 'r1'], { items: [] }],
        [['category', 'list'], { items: [] }],
      ],
    })

    await user.click(openSheet())
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('물건 이름'), '새 드릴')
    await user.selectOptions(await within(dialog).findByLabelText('방', {}, { timeout: 3000 }), 'r1')
    await user.click(within(dialog).getByRole('button', { name: '등록' }))

    expect(await within(dialog).findByText('등록하지 못했습니다.')).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: '등록' }))

    await waitFor(() => expect(createInventorySpy).toHaveBeenCalledTimes(2))
    expect(await within(dialog).findByText('등록했어요.')).toBeInTheDocument()
  })
})
