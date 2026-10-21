import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { inventoryApi } from './api'
import { testCategory, testInventory, testItem } from '@/test/inventory-fixtures'
import { renderApp } from '@/test/render-app'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('재고 편집', () => {
  it('재고값을 직접 입력해 오버라이드를 저장한다', async () => {
    const user = userEvent.setup()
    const updateSpy = vi
      .spyOn(inventoryApi, 'updateInventory')
      .mockResolvedValue(testInventory({ nameOverride: '내 드릴', version: 3 }))
    renderApp(['/inventory/v1/edit'], {
      seeds: [
        [['inventory', 'detail', 'v1'], testInventory()],
        [['item', 'detail', 'i1'], testItem()],
        [['category', 'list'], { items: [testCategory] }],
      ],
    })

    expect(screen.getByRole('heading', { name: '재고 편집' })).toBeInTheDocument()
    await user.click(screen.getByRole('checkbox', { name: /이름/ }))
    await user.clear(screen.getByLabelText('재고 이름'))
    await user.type(screen.getByLabelText('재고 이름'), '내 드릴')
    await user.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() =>
      expect(updateSpy).toHaveBeenCalledWith(
        'v1',
        expect.objectContaining({
          version: 2,
          nameOverride: '내 드릴',
          descriptionOverride: null,
          categoryIdOverride: null,
          priceAmountOverride: null,
        }),
      ),
    )
  })
})
