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
})
