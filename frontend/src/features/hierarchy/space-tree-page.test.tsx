import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { hierarchyApi } from './api'
import { testBox, testRoom, testShelf } from '@/test/inventory-fixtures'
import { renderApp } from '@/test/render-app'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('공간 지도', () => {
  it('Box를 다른 Shelf로 이동한다', async () => {
    const user = userEvent.setup()
    const moveSpy = vi.spyOn(hierarchyApi, 'moveBox').mockResolvedValue(testBox({ shelfId: 's2' }))
    const shelf2 = testShelf({ id: 's2', name: '보관함' })
    renderApp(['/space'], {
      seeds: [
        [
          ['hierarchy', 'rooms', 'tree'],
          { pages: [{ items: [testRoom()], nextCursor: null }], pageParams: [undefined] },
        ],
        [
          ['hierarchy', 'shelves', 'r1', 'tree'],
          { pages: [{ items: [testShelf()], nextCursor: null }], pageParams: [undefined] },
        ],
        [
          ['hierarchy', 'boxes', 's1', 'tree'],
          { pages: [{ items: [testBox()], nextCursor: null }], pageParams: [undefined] },
        ],
        [
          ['hierarchy', 'shelves', 'move-box', 'r1'],
          { items: [testShelf(), shelf2], nextCursor: null },
        ],
      ],
    })

    await user.click(await screen.findByRole('button', { name: 'Room 펼치기' }))
    await user.click(await screen.findByRole('button', { name: 'Shelf 펼치기' }))
    const moveButtons = await screen.findAllByRole('button', { name: '이동' })
    await user.click(moveButtons[moveButtons.length - 1])

    const dialog = await screen.findByRole('dialog')
    await user.selectOptions(within(dialog).getByLabelText('이동할 Shelf'), 's2')
    await user.click(within(dialog).getByRole('button', { name: '이동' }))

    expect(moveSpy).toHaveBeenCalledWith('b1', 's2', 1)
  })
})
