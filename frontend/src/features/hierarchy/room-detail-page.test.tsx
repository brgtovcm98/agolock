import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { sharingApi } from '@/features/sharing/api'
import { testInventory, testRoom, testShelf } from '@/test/inventory-fixtures'
import { renderApp } from '@/test/render-app'

afterEach(() => {
  vi.restoreAllMocks()
})

describe('Room 상세', () => {
  it('Room에서 바로 공유 초대를 보낸다', async () => {
    const user = userEvent.setup()
    const inviteSpy = vi.spyOn(sharingApi, 'createInvitation').mockResolvedValue({
      id: 'inv1',
      resourceType: 'ROOM',
      resourceName: '거실',
      roomId: 'r1',
      shelfId: null,
      invitedEmail: 'friend@example.com',
      permission: 'MANAGE',
      status: 'PENDING',
      expiresAt: '2026-10-23T00:00:00Z',
      createdAt: '2026-10-22T00:00:00Z',
    })
    renderApp(['/space/rooms/r1'], {
      seeds: [
        [['hierarchy', 'room', 'r1'], testRoom()],
        [['hierarchy', 'shelves', 'r1'], { items: [testShelf()], nextCursor: null }],
        [['inventory', 'location', 'r1', '', ''], { items: [testInventory()], nextCursor: null }],
        [['sharing', 'shared'], { items: [] }],
      ],
    })

    await user.click(screen.getByRole('button', { name: '공유' }))
    const dialog = screen.getByRole('dialog')
    await user.type(screen.getByLabelText('이메일'), 'friend@example.com')
    await user.selectOptions(screen.getByRole('combobox', { name: '공유 권한' }), 'MANAGE')
    await user.click(screen.getByRole('button', { name: '초대 보내기' }))

    await waitFor(() =>
      expect(inviteSpy).toHaveBeenCalledWith({
        roomId: 'r1',
        shelfId: undefined,
        invitedEmail: 'friend@example.com',
        permission: 'MANAGE',
      }),
    )
    expect(await screen.findByText(/초대를 보냈어요/)).toBeInTheDocument()
    expect(dialog).toBeInTheDocument()
  })
})
