import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { inventoryApi } from './api'
import { testCategory, testItem } from '@/test/inventory-fixtures'
import { renderApp } from '@/test/render-app'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('물건 수정', () => {
  it('영향 범위를 확인한 뒤 마스터를 저장한다', async () => {
    const user = userEvent.setup()
    const updateSpy = vi
      .spyOn(inventoryApi, 'updateItem')
      .mockResolvedValue(testItem({ name: '새 이름', version: 2 }))
    vi.spyOn(inventoryApi, 'itemUpdateImpact').mockResolvedValue({
      affectedInventoryCount: 2,
      protectedByOverrideCount: 1,
    })
    renderApp(['/items/i1/edit'], {
      seeds: [
        [['item', 'detail', 'i1'], testItem()],
        [['category', 'list'], { items: [testCategory] }],
      ],
    })

    await user.clear(screen.getByLabelText('이름'))
    await user.type(screen.getByLabelText('이름'), '새 이름')
    await user.click(screen.getByRole('button', { name: '저장' }))

    expect(await screen.findByText('재고에 반영할까요?')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '반영하고 저장' }))

    await waitFor(() =>
      expect(updateSpy).toHaveBeenCalledWith(
        'i1',
        expect.objectContaining({ version: 1, name: '새 이름' }),
      ),
    )
  })
})
