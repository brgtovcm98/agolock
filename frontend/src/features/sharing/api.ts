import { apiFetch } from '@/shared/api/client'

export type SharePermission = 'VIEW' | 'EDIT' | 'MANAGE'
export type InvitationStatus = 'PENDING' | 'ACCEPTED' | 'DECLINED' | 'REVOKED' | 'EXPIRED'

export type Share = {
  id: string
  resourceType: 'ROOM' | 'SHELF'
  resourceName: string
  roomId: string | null
  shelfId: string | null
  permission: SharePermission
  granteeUserId: string
  granteeDisplayName: string | null
  createdAt: string
}

export type Invitation = {
  id: string
  resourceType: 'ROOM' | 'SHELF'
  resourceName: string
  roomId: string | null
  shelfId: string | null
  invitedEmail: string
  permission: SharePermission
  status: InvitationStatus
  expiresAt: string
  createdAt: string
}

export type SharingTarget = { id: string; name: string }

export const sharingApi = {
  shared: () => apiFetch<{ items: Share[] }>('/shared'),
  sharedShelf: (shelfId: string) =>
    apiFetch<{ id: string; roomId: string; name: string; effectivePermission: string }>(
      `/shared/shelves/${shelfId}`,
    ),
  shares: () => apiFetch<{ items: Share[] }>('/shares'),
  invitations: () => apiFetch<{ items: Invitation[] }>('/share-invitations'),
  received: () => apiFetch<{ items: Invitation[] }>('/share-invitations/received'),
  rooms: () => apiFetch<{ items: SharingTarget[] }>('/rooms?limit=100'),
  createInvitation: (target: {
    roomId?: string
    shelfId?: string
    invitedEmail: string
    permission: SharePermission
  }) =>
    apiFetch<Invitation>('/share-invitations', {
      method: 'POST',
      body: JSON.stringify({
        roomId: target.roomId ?? null,
        shelfId: target.shelfId ?? null,
        invitedEmail: target.invitedEmail,
        permission: target.permission,
      }),
    }),
  accept: (invitationId: string) =>
    apiFetch<Share>(`/share-invitations/received/${invitationId}/accept`, { method: 'POST' }),
  decline: (invitationId: string) =>
    apiFetch<void>(`/share-invitations/received/${invitationId}/decline`, { method: 'POST' }),
  acceptByToken: (token: string) =>
    apiFetch<Share>('/share-invitations/accept', {
      method: 'POST',
      body: JSON.stringify({ token }),
    }),
  declineByToken: (token: string) =>
    apiFetch<void>('/share-invitations/decline', {
      method: 'POST',
      body: JSON.stringify({ token }),
    }),
  revokeInvitation: (invitationId: string) =>
    apiFetch<void>(`/share-invitations/${invitationId}`, { method: 'DELETE' }),
  revokeShare: (shareId: string) => apiFetch<void>(`/shares/${shareId}`, { method: 'DELETE' }),
  updateShare: (shareId: string, permission: SharePermission) =>
    apiFetch<Share>(`/shares/${shareId}`, {
      method: 'PATCH',
      body: JSON.stringify({ permission }),
    }),
}
