import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { inventoryApi } from './api'
import { ApiError } from '@/shared/api/client'
import { testInventory, testItem, testRoom } from '@/test/inventory-fixtures'
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
    const createItemSpy = vi.spyOn(inventoryApi, 'createItem').mockResolvedValue(testItem())
    const createInventorySpy = vi
      .spyOn(inventoryApi, 'createInventory')
      .mockResolvedValue(testInventory())
    vi.spyOn(inventoryApi, 'inventories').mockResolvedValue({ items: [], nextCursor: null })
    vi.spyOn(inventoryApi, 'itemInventories').mockResolvedValue({ items: [], nextCursor: null })

    renderApp(['/'], {
      seeds: [
        [['hierarchy', 'rooms', 'picker'], { items: [testRoom()] }],
        [['hierarchy', 'shelves', 'picker', 'r1'], { items: [] }],
        [['category', 'list'], { items: [] }],
      ],
    })

    await user.click(openSheet())
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('물건 이름'), '새 드릴')
    await user.selectOptions(await within(dialog).findByLabelText('방'), 'r1')
    await user.click(within(dialog).getByRole('button', { name: '등록' }))

    await waitFor(() => expect(createItemSpy).toHaveBeenCalledTimes(1))
    expect(createItemSpy).toHaveBeenCalledWith(
      expect.objectContaining({ name: '새 드릴', idempotencyKey: expect.any(String) }),
    )
    expect(createInventorySpy).toHaveBeenCalledWith(
      expect.objectContaining({
        itemId: 'i1',
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

  it('재고 생성이 실패하면 물건을 유지하고 재시도한다', async () => {
    const user = userEvent.setup()
    const createItemSpy = vi.spyOn(inventoryApi, 'createItem').mockResolvedValue(testItem())
    const createInventorySpy = vi
      .spyOn(inventoryApi, 'createInventory')
      .mockRejectedValueOnce(
        new ApiError({
          title: '재고 생성 실패',
          status: 500,
          code: 'SERVER_ERROR',
          detail: '재고를 만들지 못했습니다.',
        }),
      )
      .mockResolvedValueOnce(testInventory())
    vi.spyOn(inventoryApi, 'inventories').mockResolvedValue({ items: [], nextCursor: null })
    vi.spyOn(inventoryApi, 'itemInventories').mockResolvedValue({ items: [], nextCursor: null })

    renderApp(['/'], {
      seeds: [
        [['hierarchy', 'rooms', 'picker'], { items: [testRoom()] }],
        [['hierarchy', 'shelves', 'picker', 'r1'], { items: [] }],
        [['category', 'list'], { items: [] }],
      ],
    })

    await user.click(openSheet())
    const dialog = screen.getByRole('dialog')
    await user.type(within(dialog).getByLabelText('물건 이름'), '새 드릴')
    await user.selectOptions(await within(dialog).findByLabelText('방'), 'r1')
    await user.click(within(dialog).getByRole('button', { name: '등록' }))

    expect(await within(dialog).findByText('재고를 만들지 못했습니다.')).toBeInTheDocument()
    expect(
      within(dialog).getByText('물건 정보는 이미 저장됐어요. 재고 등록만 다시 시도합니다.'),
    ).toBeInTheDocument()

    await user.click(within(dialog).getByRole('button', { name: '재고 등록 다시 시도' }))

    await waitFor(() => expect(createInventorySpy).toHaveBeenCalledTimes(2))
    expect(createItemSpy).toHaveBeenCalledTimes(1)
    expect(await within(dialog).findByText('등록했어요.')).toBeInTheDocument()
  })
})
