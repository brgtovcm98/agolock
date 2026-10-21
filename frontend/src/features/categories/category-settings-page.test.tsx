import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { inventoryApi } from '@/features/inventory/api'
import { testCategory } from '@/test/inventory-fixtures'
import { renderApp } from '@/test/render-app'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('카테고리 관리', () => {
  it('카테고리를 만들고 삭제 확인을 연다', async () => {
    const user = userEvent.setup()
    const createSpy = vi.spyOn(inventoryApi, 'createCategory').mockResolvedValue({
      id: 'c2',
      name: '캠핑',
      color: null,
      sortOrder: 1,
    })
    renderApp(['/settings/categories'], {
      seeds: [[['category', 'list'], { items: [testCategory] }]],
    })

    expect(screen.getByRole('heading', { name: '카테고리 관리' })).toBeInTheDocument()
    expect(screen.getByText('공구')).toBeInTheDocument()

    await user.type(screen.getByLabelText('새 카테고리'), '캠핑')
    await user.click(screen.getByRole('button', { name: /추가/ }))

    expect(createSpy).toHaveBeenCalledWith('캠핑')

    await user.click(screen.getByRole('button', { name: '공구 삭제' }))
    expect(screen.getByRole('heading', { name: '카테고리를 삭제할까요?' })).toBeInTheDocument()
  })

  it('카테고리 이름을 바꾸고 다른 카테고리로 병합한다', async () => {
    const user = userEvent.setup()
    const updateSpy = vi
      .spyOn(inventoryApi, 'updateCategory')
      .mockResolvedValue({ ...testCategory, name: '연장' })
    const mergeSpy = vi.spyOn(inventoryApi, 'mergeCategory').mockResolvedValue({
      mergedItemCount: 2,
      mergedInventoryCount: 3,
    })
    vi.spyOn(inventoryApi, 'categories').mockResolvedValue({
      items: [
        testCategory,
        { id: 'c2', name: '캠핑', color: null, sortOrder: 1 },
      ],
    })
    renderApp(['/settings/categories'], {
      seeds: [
        [
          ['category', 'list'],
          {
            items: [
              testCategory,
              { id: 'c2', name: '캠핑', color: null, sortOrder: 1 },
            ],
          },
        ],
      ],
    })

    await user.click(screen.getByRole('button', { name: '공구 이름 변경' }))
    await user.clear(screen.getByLabelText('카테고리 이름'))
    await user.type(screen.getByLabelText('카테고리 이름'), '연장')
    await user.click(screen.getByRole('button', { name: '저장' }))
    expect(updateSpy).toHaveBeenCalledWith('c1', { name: '연장' })

    await user.click(screen.getByRole('button', { name: '공구 병합' }))
    await user.selectOptions(screen.getByRole('combobox', { name: '옮길 카테고리' }), 'c2')
    await user.click(screen.getByRole('button', { name: '병합' }))
    expect(mergeSpy).toHaveBeenCalledWith('c1', 'c2')
    expect(await screen.findByText(/물건 2개, 재고 3개/)).toBeInTheDocument()
  })
})
