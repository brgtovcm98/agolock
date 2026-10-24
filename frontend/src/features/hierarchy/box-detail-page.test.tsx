import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { testBox, testInventory, testRoom, testShelf } from '@/test/inventory-fixtures'
import { renderApp } from '@/test/render-app'

describe('Box 상세', () => {
  it('직접 위치 재고와 이 위치에 등록을 연결한다', async () => {
    const user = userEvent.setup()
    renderApp(['/space/boxes/b1'], {
      seeds: [
        [['hierarchy', 'box', 'b1'], testBox()],
        [['hierarchy', 'shelf', 's1'], testShelf()],
        [
          ['inventory', 'location', 'r1', 's1', 'b1'],
          { pages: [{ items: [testInventory()], nextCursor: null }], pageParams: [undefined] },
        ],
        [['hierarchy', 'rooms', 'picker'], { items: [testRoom()] }],
        [['hierarchy', 'shelves', 'picker', 'owned', 'r1'], { items: [testShelf()] }],
        [['hierarchy', 'boxes', 'picker', 'owned', 's1'], { items: [testBox()] }],
      ],
    })

    expect(screen.getByRole('heading', { name: '파란 박스' })).toBeInTheDocument()
    expect(screen.getByText('직접 위치 재고')).toBeInTheDocument()
    expect(screen.getByText('전동 드릴 18V')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /이 위치에 등록/ }))

    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('heading', { name: '물건 등록' })).toBeInTheDocument()
    expect(await within(dialog).findByDisplayValue('거실')).toBeInTheDocument()
    expect(within(dialog).getAllByText('거실').length).toBeGreaterThan(0)
    expect(within(dialog).getAllByText(/공구함/).length).toBeGreaterThan(0)
  })
})

describe('공유 Box 상세', () => {
  it('공유 위치 문맥으로 등록 시트를 열고 새 물건 모드를 막는다', async () => {
    const user = userEvent.setup()
    const sharedBox = testBox({ isShared: true, effectivePermission: 'MANAGE' })
    renderApp(['/space/boxes/b1'], {
      seeds: [
        [['hierarchy', 'box', 'b1'], sharedBox],
        [
          ['hierarchy', 'shelf', 's1'],
          testShelf({ isShared: true, effectivePermission: 'MANAGE' }),
        ],
        [
          ['inventory', 'location', 'r1', 's1', 'b1'],
          { pages: [{ items: [], nextCursor: null }], pageParams: [undefined] },
        ],
        [
          ['sharing', 'shared'],
          {
            items: [
              {
                id: 'sh1',
                resourceType: 'ROOM',
                resourceName: '민지네 베란다',
                roomId: 'r1',
                shelfId: null,
                permission: 'MANAGE',
                granteeUserId: 'u1',
                granteeDisplayName: null,
                createdAt: '2026-10-01T00:00:00Z',
              },
            ],
          },
        ],
        [['hierarchy', 'shelves', 'picker', 'shared', 'r1'], { items: [testShelf()] }],
        [['hierarchy', 'boxes', 'picker', 'shared', 's1'], { items: [testBox()] }],
      ],
    })

    await user.click(screen.getByRole('button', { name: /이 위치에 등록/ }))
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('tab', { name: '새 물건' })).toBeDisabled()
    expect(within(dialog).getByRole('combobox', { name: '대상 위치' })).toBeDisabled()
    expect(within(dialog).getByLabelText('물건 검색')).toBeInTheDocument()
  })
})
