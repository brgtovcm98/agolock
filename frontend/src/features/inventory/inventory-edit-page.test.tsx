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
    vi.spyOn(inventoryApi, 'inventory').mockResolvedValue(
      testInventory({ nameOverride: '내 드릴', displayName: '내 드릴', version: 3 }),
    )
    renderApp(['/inventory/v1/edit'], {
      seeds: [
        [['inventory', 'detail', 'v1'], testInventory()],
        [['item', 'detail', 'i1'], testItem()],
        [['category', 'list', 'i1'], { items: [testCategory] }],
      ],
    })

    expect(screen.getByRole('heading', { name: '재고 편집' })).toBeInTheDocument()
    await user.click(screen.getByRole('checkbox', { name: /이름/ }))
    await user.clear(await screen.findByLabelText('재고 이름'))
    await user.type(screen.getByLabelText('재고 이름'), '내 드릴')
    await user.click(screen.getByRole('button', { name: '저장' }))

    await waitFor(() =>
      expect(updateSpy).toHaveBeenCalledWith(
        'v1',
        expect.objectContaining({
          version: 2,
          nameOverride: '내 드릴',
        }),
      ),
    )
    expect(await screen.findByRole('heading', { name: '내 드릴' })).toBeInTheDocument()
  })
})

it('변경하지 않으면 저장할 수 없다', () => {
  renderApp(['/inventory/v1/edit'], {
    seeds: [
      [['inventory', 'detail', 'v1'], testInventory()],
      [['item', 'detail', 'i1'], testItem()],
      [['category', 'list', 'i1'], { items: [testCategory], nextCursor: null }],
    ],
  })
  expect(screen.getByRole('button', { name: '저장' })).toBeDisabled()
})

it('버전 충돌에서도 입력을 보존하고 최신 버전으로 명시적으로 다시 저장한다', async () => {
  const user = userEvent.setup()
  const { ApiError } = await import('@/shared/api/client')
  const update = vi
    .spyOn(inventoryApi, 'updateInventory')
    .mockRejectedValueOnce(new ApiError({ title: '충돌', status: 409, code: 'VERSION_CONFLICT' }))
    .mockResolvedValueOnce(testInventory({ version: 4 }))
  vi.spyOn(inventoryApi, 'inventory').mockResolvedValue(
    testInventory({ version: 3, descriptionOverride: '최신 설명' }),
  )
  renderApp(['/inventory/v1/edit'], {
    seeds: [
      [['inventory', 'detail', 'v1'], testInventory()],
      [['item', 'detail', 'i1'], testItem()],
      [['category', 'list', 'i1'], { items: [testCategory], nextCursor: null }],
    ],
  })
  await user.click(screen.getByRole('checkbox', { name: /이름/ }))
  await user.clear(await screen.findByLabelText('재고 이름'))
  await user.type(screen.getByLabelText('재고 이름'), '내 입력')
  await user.click(screen.getByRole('button', { name: '저장' }))
  await waitFor(() => expect(screen.getByLabelText('재고 설명')).toHaveValue('최신 설명'))
  expect(screen.getByLabelText('재고 이름')).toHaveValue('내 입력')
  expect(update).toHaveBeenCalledTimes(1)
  await user.click(screen.getByRole('button', { name: '저장' }))
  await waitFor(() =>
    expect(update).toHaveBeenLastCalledWith('v1', { version: 3, nameOverride: '내 입력' }),
  )
})

it('같은 필드 충돌은 내 입력 또는 최신 값 선택 전까지 저장을 막는다', async () => {
  const user = userEvent.setup()
  const { ApiError } = await import('@/shared/api/client')
  vi.spyOn(inventoryApi, 'updateInventory').mockRejectedValue(
    new ApiError({ title: '충돌', status: 409, code: 'VERSION_CONFLICT' }),
  )
  vi.spyOn(inventoryApi, 'inventory').mockResolvedValue(
    testInventory({ version: 3, nameOverride: '서버 이름' }),
  )
  renderApp(['/inventory/v1/edit'], {
    seeds: [
      [['inventory', 'detail', 'v1'], testInventory()],
      [['item', 'detail', 'i1'], testItem()],
      [['category', 'list', 'i1'], { items: [testCategory], nextCursor: null }],
    ],
  })
  await user.click(screen.getByRole('checkbox', { name: /이름/ }))
  await user.clear(await screen.findByLabelText('재고 이름'))
  await user.type(screen.getByLabelText('재고 이름'), '내 이름')
  await user.click(screen.getByRole('button', { name: '저장' }))
  expect(await screen.findByRole('button', { name: '내 입력 사용' })).toBeInTheDocument()
  expect(screen.getByRole('button', { name: '저장' })).toBeDisabled()
  expect(screen.getByLabelText('재고 이름')).toHaveValue('내 이름')
  expect(screen.getByText('내 입력: 내 이름 · 최신 값: 서버 이름')).toBeInTheDocument()
  await user.click(screen.getByRole('button', { name: '최신 값 사용' }))
  expect(screen.getByLabelText('재고 이름')).toHaveValue('서버 이름')
  expect(screen.getByRole('button', { name: '저장' })).toBeDisabled()
})

it('충돌 후 최신 조회가 거부되어도 입력을 유지하고 저장을 막는다', async () => {
  const user = userEvent.setup()
  const { ApiError } = await import('@/shared/api/client')
  vi.spyOn(inventoryApi, 'updateInventory').mockRejectedValue(
    new ApiError({ title: '충돌', status: 409, code: 'VERSION_CONFLICT' }),
  )
  vi.spyOn(inventoryApi, 'inventory').mockRejectedValue(
    new ApiError({ title: '접근 거부', status: 403, code: 'FORBIDDEN' }),
  )
  renderApp(['/inventory/v1/edit'], {
    seeds: [
      [['inventory', 'detail', 'v1'], testInventory()],
      [['item', 'detail', 'i1'], testItem()],
      [['category', 'list', 'i1'], { items: [testCategory], nextCursor: null }],
    ],
  })
  await user.click(screen.getByRole('checkbox', { name: /이름/ }))
  await user.clear(await screen.findByLabelText('재고 이름'))
  await user.type(screen.getByLabelText('재고 이름'), '유지할 입력')
  await user.click(screen.getByRole('button', { name: '저장' }))
  expect(
    await screen.findByText('최신 정보를 확인할 수 없어 저장을 중단했어요. 입력은 유지됩니다.'),
  ).toBeInTheDocument()
  expect(screen.getByLabelText('재고 이름')).toHaveValue('유지할 입력')
  expect(screen.getByRole('button', { name: '저장' })).toBeDisabled()
})

it('최신 값 재조회 중에는 입력을 잠가 뒤늦은 병합이 새 입력을 덮어쓰지 않는다', async () => {
  const user = userEvent.setup()
  const { ApiError } = await import('@/shared/api/client')
  vi.spyOn(inventoryApi, 'updateInventory').mockRejectedValue(
    new ApiError({ title: '충돌', status: 409, code: 'VERSION_CONFLICT' }),
  )
  let finish: (value: ReturnType<typeof testInventory>) => void = () => undefined
  const latest = new Promise<ReturnType<typeof testInventory>>((resolve) => {
    finish = resolve
  })
  const read = vi.spyOn(inventoryApi, 'inventory').mockReturnValue(latest)
  renderApp(['/inventory/v1/edit'], {
    seeds: [
      [['inventory', 'detail', 'v1'], testInventory()],
      [['item', 'detail', 'i1'], testItem()],
      [['category', 'list', 'i1'], { items: [testCategory], nextCursor: null }],
    ],
  })
  await user.click(screen.getByRole('checkbox', { name: /이름/ }))
  await user.clear(await screen.findByLabelText('재고 이름'))
  await user.type(screen.getByLabelText('재고 이름'), '입력 보존')
  await user.click(screen.getByRole('button', { name: '저장' }))
  await waitFor(() => expect(read).toHaveBeenCalled())
  try {
    expect(screen.getByLabelText('재고 이름')).toBeDisabled()
  } finally {
    finish(testInventory({ version: 3 }))
  }
  await waitFor(() => expect(screen.getByLabelText('재고 이름')).toBeEnabled())
  expect(screen.getByLabelText('재고 이름')).toHaveValue('입력 보존')
})
