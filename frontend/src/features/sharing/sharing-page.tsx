import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Check, Plus, X } from 'lucide-react'
import { useState } from 'react'
import { Link } from 'react-router-dom'
import { sharingApi, type InvitationStatus, type SharePermission } from './api'
import { useAuth } from '@/features/auth/auth-provider'
import { ApiError } from '@/shared/api/client'
import { Alert } from '@/shared/ui/alert'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { MetaText, PageTitle, SectionTitle } from '@/shared/ui/typography'
import { TextField } from '@/shared/ui/text-field'
import { permissionDescription, permissionLabel } from '@/shared/lib/permission'

const invitationStatusLabels: Record<InvitationStatus, string> = {
  PENDING: '대기',
  ACCEPTED: '수락',
  DECLINED: '거절',
  REVOKED: '철회',
  EXPIRED: '만료',
}

export function SharingPage() {
  const queryClient = useQueryClient()
  const { config } = useAuth()
  const emailDiscoveryEnabled = config?.invitationEmailDiscoveryEnabled ?? false
  const [email, setEmail] = useState('')
  const [roomId, setRoomId] = useState('')
  const [permission, setPermission] = useState<SharePermission>('VIEW')
  const [error, setError] = useState<string | null>(null)

  const sharedQuery = useQuery({ queryKey: ['sharing', 'shared'], queryFn: sharingApi.shared })
  const sharesQuery = useQuery({ queryKey: ['sharing', 'shares'], queryFn: sharingApi.shares })
  const invitationsQuery = useQuery({
    queryKey: ['sharing', 'invitations'],
    queryFn: sharingApi.invitations,
  })
  const receivedQuery = useQuery({
    queryKey: ['sharing', 'received'],
    queryFn: sharingApi.received,
    enabled: emailDiscoveryEnabled,
  })
  const roomsQuery = useQuery({
    queryKey: ['hierarchy', 'rooms', 'sharing-picker'],
    queryFn: sharingApi.rooms,
  })

  const invalidate = async () => {
    await Promise.all([
      queryClient.invalidateQueries({ queryKey: ['sharing'] }),
      queryClient.invalidateQueries({ queryKey: ['hierarchy'] }),
      queryClient.invalidateQueries({ queryKey: ['inventory'] }),
    ])
  }
  const fail = (cause: unknown) =>
    setError(cause instanceof ApiError ? cause.message : '요청을 처리하지 못했습니다.')

  const inviteMutation = useMutation({
    mutationFn: () =>
      sharingApi.createInvitation({ roomId, invitedEmail: email, permission }),
    onSuccess: async () => {
      setEmail('')
      setError(null)
      await invalidate()
    },
    onError: fail,
  })
  const acceptMutation = useMutation({
    mutationFn: (id: string) => sharingApi.accept(id),
    onSuccess: async () => {
      setError(null)
      await invalidate()
    },
    onError: fail,
  })
  const declineMutation = useMutation({
    mutationFn: (id: string) => sharingApi.decline(id),
    onSuccess: async () => {
      setError(null)
      await invalidate()
    },
    onError: fail,
  })
  const updateShareMutation = useMutation({
    mutationFn: ({ id, permission }: { id: string; permission: SharePermission }) =>
      sharingApi.updateShare(id, permission),
    onSuccess: async () => {
      setError(null)
      await invalidate()
    },
    onError: fail,
  })
  const revokeShareMutation = useMutation({
    mutationFn: (id: string) => sharingApi.revokeShare(id),
    onSuccess: async () => {
      setError(null)
      await invalidate()
    },
    onError: fail,
  })
  const revokeInvitationMutation = useMutation({
    mutationFn: (id: string) => sharingApi.revokeInvitation(id),
    onSuccess: async () => {
      setError(null)
      await invalidate()
    },
    onError: fail,
  })

  const shared = sharedQuery.data?.items ?? []
  const shares = sharesQuery.data?.items ?? []
  const invitations = invitationsQuery.data?.items ?? []
  const received = receivedQuery.data?.items ?? []
  const rooms = roomsQuery.data?.items ?? []

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-1">
        <p className="label-caps text-foreground/60">공유</p>
        <PageTitle>공유</PageTitle>
        <MetaText>초대를 수락한 사람만 공유 공간을 볼 수 있습니다.</MetaText>
      </div>
      {error ? <Alert variant="error">{error}</Alert> : null}

      <div className="grid gap-4 lg:grid-cols-2">
        <Card>
          <CardBody className="gap-3">
            <SectionTitle>Room 초대</SectionTitle>
            <form
              className="grid gap-2"
              onSubmit={(event) => {
                event.preventDefault()
                if (email.trim() && roomId) inviteMutation.mutate()
              }}
            >
              <TextField
                label="이메일"
                type="email"
                value={email}
                onChange={(event) => setEmail(event.target.value)}
              />
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-black">Room</span>
                <select
                  value={roomId}
                  onChange={(event) => setRoomId(event.target.value)}
                  className="border-border bg-card h-12 border-[2.5px] px-3 text-sm"
                >
                  <option value="">선택</option>
                  {rooms.map((room) => (
                    <option key={room.id} value={room.id}>
                      {room.name}
                    </option>
                  ))}
                </select>
              </label>
              <label className="flex flex-col gap-1.5">
                <span className="text-xs font-black">권한</span>
                <select
                  value={permission}
                  onChange={(event) => setPermission(event.target.value as SharePermission)}
                  className="border-border bg-card h-12 border-[2.5px] px-3 text-sm"
                >
                  <option value="VIEW">{permissionLabel('VIEW')}</option>
                  <option value="EDIT">{permissionLabel('EDIT')}</option>
                  <option value="MANAGE">{permissionLabel('MANAGE')}</option>
                </select>
              </label>
              <p className="text-foreground/60 text-[11px]">{permissionDescription(permission)}</p>
              <Button type="submit" disabled={inviteMutation.isPending || !email.trim() || !roomId}>
                <Plus aria-hidden="true" className="size-4" />
                초대 보내기
              </Button>
            </form>
          </CardBody>
        </Card>

        <Card>
          <CardBody className="gap-3">
            <SectionTitle>받은 초대</SectionTitle>
            {!emailDiscoveryEnabled ? (
              <p className="text-xs">메일의 초대 링크로 수락해 주세요.</p>
            ) : null}
            {emailDiscoveryEnabled && received.length === 0 ? (
              <p className="text-xs">대기 중인 초대가 없습니다.</p>
            ) : null}
            {received.map((invitation) => (
              <div key={invitation.id} className="border-border flex flex-col gap-2 border-2 p-3">
                <div className="flex items-center justify-between gap-2">
                  <span className="text-sm font-black">{invitation.resourceName}</span>
                  <Badge variant="info">{permissionLabel(invitation.permission)}</Badge>
                </div>
                <span className="text-xs">{invitation.invitedEmail}</span>
                <div className="flex gap-2">
                  <Button
                    type="button"
                    size="sm"
                    onClick={() => acceptMutation.mutate(invitation.id)}
                  >
                    <Check aria-hidden="true" className="size-4" /> 수락
                  </Button>
                  <Button
                    type="button"
                    size="sm"
                    variant="secondary"
                    onClick={() => declineMutation.mutate(invitation.id)}
                  >
                    <X aria-hidden="true" className="size-4" /> 거절
                  </Button>
                </div>
              </div>
            ))}
          </CardBody>
        </Card>
      </div>

      <Card>
        <CardBody className="gap-3">
          <SectionTitle>공유받은 공간</SectionTitle>
          {shared.length === 0 ? <p className="text-xs">아직 공유받은 공간이 없습니다.</p> : null}
          <div className="grid gap-2 md:grid-cols-2">
            {shared.map((share) => (
              <Link
                key={share.id}
                to={
                  share.resourceType === 'ROOM'
                    ? `/space/rooms/${share.roomId}`
                    : `/space/shelves/${share.shelfId}`
                }
                className="border-border bg-blue-soft flex items-center justify-between gap-2 border-2 p-3 hover:underline"
              >
                <span className="text-sm font-black">{share.resourceName}</span>
                <Badge variant="info">{permissionLabel(share.permission)}</Badge>
              </Link>
            ))}
          </div>
        </CardBody>
      </Card>

      <Card>
        <CardBody className="gap-3">
          <SectionTitle>보낸 초대·공유</SectionTitle>
          {invitations.map((invitation) => (
            <div
              key={invitation.id}
              className="border-border flex flex-wrap items-center gap-2 border-2 p-3 text-xs"
            >
              <span className="font-black">{invitation.resourceName}</span>
              <span>{invitation.invitedEmail}</span>
              <Badge>{invitationStatusLabels[invitation.status]}</Badge>
              {invitation.status === 'PENDING' ? (
                <Button
                  type="button"
                  size="sm"
                  variant="secondary"
                  onClick={() => revokeInvitationMutation.mutate(invitation.id)}
                >
                  철회
                </Button>
              ) : null}
            </div>
          ))}
          {shares.map((share) => (
            <div
              key={share.id}
              className="border-border flex flex-wrap items-center gap-2 border-2 p-3 text-xs"
            >
              <span className="font-black">{share.resourceName}</span>
              <span>{share.granteeDisplayName ?? share.granteeUserId}</span>
              <select
                aria-label="공유 권한"
                value={share.permission}
                onChange={(event) =>
                  updateShareMutation.mutate({
                    id: share.id,
                    permission: event.target.value as SharePermission,
                  })
                }
                className="border-border bg-card border-2 px-2 py-1 text-xs"
              >
                <option value="VIEW">{permissionLabel('VIEW')}</option>
                <option value="EDIT">{permissionLabel('EDIT')}</option>
                <option value="MANAGE">{permissionLabel('MANAGE')}</option>
              </select>
              <Button
                type="button"
                size="sm"
                variant="secondary"
                onClick={() => revokeShareMutation.mutate(share.id)}
              >
                공유 철회
              </Button>
            </div>
          ))}
          {invitations.length === 0 && shares.length === 0 ? (
            <p className="text-xs">보낸 초대나 공유가 없습니다.</p>
          ) : null}
        </CardBody>
      </Card>
    </div>
  )
}
