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
      version: 1,
      changedFields: ['name'],
      totalInventoryCount: 3,
      fullyProtectedInventoryCount: 1,
      fieldImpacts: [{ field: 'name', affectedInventoryCount: 2, protectedInventoryCount: 1 }],
    })
    renderApp(['/items/i1/edit'], {
      seeds: [
        [['item', 'detail', 'i1'], testItem()],
        [['category', 'list', 'i1'], { items: [testCategory] }],
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

it('미리보기와 저장에 동일한 변경분을 전달한다', async () => {
  const user = userEvent.setup()
  const preview = vi.spyOn(inventoryApi, 'itemUpdateImpact').mockResolvedValue({
    version: 1,
    changedFields: ['description'],
    totalInventoryCount: 1,
    affectedInventoryCount: 1,
    fullyProtectedInventoryCount: 0,
    fieldImpacts: [{ field: 'description', affectedInventoryCount: 1, protectedInventoryCount: 0 }],
  })
  const update = vi
    .spyOn(inventoryApi, 'updateItem')
    .mockResolvedValue(testItem({ version: 2, description: '' }))
  renderApp(['/items/i1/edit'], {
    seeds: [
      [['item', 'detail', 'i1'], testItem({ description: '원래 설명' })],
      [['category', 'list', 'i1'], { items: [testCategory], nextCursor: null }],
    ],
  })
  await user.clear(screen.getByLabelText('설명 (선택)'))
  await user.click(screen.getByRole('button', { name: '저장' }))
  await screen.findByText('재고에 반영할까요?')
  expect(preview).toHaveBeenCalledWith('i1', { version: 1, description: '' })
  await user.click(screen.getByRole('button', { name: '반영하고 저장' }))
  await waitFor(() => expect(update).toHaveBeenCalledWith('i1', { version: 1, description: '' }))
})

it('편집 중 이동하면 입력 취소 확인을 요청한다', async () => {
  const user = userEvent.setup()
  renderApp(['/items/i1/edit'], {
    seeds: [
      [['item', 'detail', 'i1'], testItem()],
      [['category', 'list', 'i1'], { items: [testCategory], nextCursor: null }],
    ],
  })
  await user.type(screen.getByLabelText('이름'), '수정')
  await user.click(screen.getByRole('link', { name: '취소' }))
  expect(await screen.findByRole('heading', { name: '입력을 취소할까요?' })).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '취소' }))
  expect(screen.getByLabelText('이름')).toHaveValue('전동 드릴 18V수정')
})
