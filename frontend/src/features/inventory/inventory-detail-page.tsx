import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { MapPin, Package, Trash2 } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { inventoryApi, type InventoryStatus } from './api'
import { MoveInventoryDialog } from './components/move-inventory-dialog'
import { MovementTimeline } from './components/movement-timeline'
import { useLocationPath } from './use-location-path'
import { InventoryImages } from '@/features/media/inventory-images'
import { ApiError } from '@/shared/api/client'
import { Alert } from '@/shared/ui/alert'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { ConfirmDialog } from '@/shared/ui/confirm-dialog'
import { StatusTag } from '@/shared/ui/status-tag'
import { MetaText, PageTitle, SectionTitle } from '@/shared/ui/typography'
import { formatPrice } from '@/shared/lib/format'
import { canEdit, canManage, permissionLabel } from '@/shared/lib/permission'

const statusOptions: Array<{ value: InventoryStatus; label: string }> = [
  { value: 'IN_USE', label: '사용 중' },
  { value: 'STORED', label: '보관' },
  { value: 'DISPOSED', label: '폐기' },
]

function DetailRow({
  label,
  children,
  override,
}: {
  label: string
  children: ReactNode
  override?: boolean
}) {
  return (
    <div className="border-border grid grid-cols-[96px_1fr] border-b-2 last:border-b-0">
      <dt className="bg-yellow-soft border-border border-r-2 px-3 py-2 text-[11px] font-black">
        {label}
      </dt>
      <dd className="flex flex-wrap items-center gap-2 px-3 py-2 text-xs font-medium">
        {children}
        {override ? (
          <Badge variant="override" className="h-5 px-1.5 text-[10px]">
            재고값
          </Badge>
        ) : null}
      </dd>
    </div>
  )
}

export function InventoryDetailPage() {
  const { inventoryId = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [statusDraftOverride, setStatusDraftOverride] = useState<InventoryStatus | null>(null)
  const [confirmDisposeOpen, setConfirmDisposeOpen] = useState(false)
  const [deleteOpen, setDeleteOpen] = useState(false)
  const [moveOpen, setMoveOpen] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const inventoryQuery = useQuery({
    queryKey: ['inventory', 'detail', inventoryId],
    queryFn: () => inventoryApi.inventory(inventoryId),
    enabled: Boolean(inventoryId),
  })
  const categoriesQuery = useQuery({
    queryKey: ['category', 'list'],
    queryFn: inventoryApi.categories,
  })

  const inventory = inventoryQuery.data
  const { path: locationPath } = useLocationPath(inventory?.location)
  const statusDraft = statusDraftOverride ?? inventory?.status ?? 'STORED'

  const updateMutation = useMutation({
    mutationFn: (status: InventoryStatus) =>
      inventoryApi.updateInventory(inventoryId, {
        version: inventory?.version ?? 0,
        status,
        confirmDisposal: status === 'DISPOSED',
      }),
    onSuccess: async () => {
      setError(null)
      setStatusDraftOverride(null)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['inventory'] }),
        queryClient.invalidateQueries({ queryKey: ['hierarchy'] }),
      ])
    },
    onError: async (cause) => {
      if (cause instanceof ApiError && cause.status === 409) {
        setError('다른 곳에서 먼저 수정했어요. 최신 정보를 다시 불러왔습니다.')
        setStatusDraftOverride(null)
        await inventoryQuery.refetch()
        return
      }
      setError(cause instanceof ApiError ? cause.message : '상태를 바꾸지 못했습니다.')
    },
  })

  const deleteMutation = useMutation({
    mutationFn: () => inventoryApi.deleteInventory(inventoryId),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['inventory'] }),
        queryClient.invalidateQueries({ queryKey: ['hierarchy'] }),
      ])
      navigate('/inventory', { replace: true })
    },
    onError: (cause) =>
      setError(cause instanceof ApiError ? cause.message : '삭제하지 못했습니다.'),
  })

  if (inventoryQuery.isLoading) {
    return (
      <p role="status" className="text-sm font-bold">
        재고를 불러오는 중…
      </p>
    )
  }
  if (inventoryQuery.isError || !inventory) {
    return (
      <div className="flex flex-col items-start gap-3">
        <Alert variant="error">재고를 찾을 수 없습니다.</Alert>
        <Button asChild variant="secondary">
          <Link to="/inventory">재고 목록으로</Link>
        </Button>
      </div>
    )
  }

  const categoryName =
    categoriesQuery.data?.items.find(
      (category) => category.id === (inventory.categoryIdOverride ?? inventory.categoryId),
    )?.name ?? '미분류'
  const editable = canEdit(inventory.effectivePermission)
  const manageable = canManage(inventory.effectivePermission)
  const statusChanged = statusDraft !== inventory.status

  return (
    <div className="flex flex-col gap-5">
      <div className="flex flex-col gap-1">
        <Link to="/inventory" className="text-xs font-bold underline underline-offset-2">
          ← 재고 목록
        </Link>
        <div className="flex flex-wrap items-center gap-2">
          <PageTitle>{inventory.displayName}</PageTitle>
          <StatusTag status={inventory.status} />
          <Badge variant="info">{permissionLabel(inventory.effectivePermission)}</Badge>
          {inventory.isShared ? <Badge>공유</Badge> : null}
        </div>
        <MetaText className="flex items-center gap-1">
          <MapPin aria-hidden="true" className="size-3.5" />
          {locationPath}
        </MetaText>
      </div>

      {error ? <Alert variant="error">{error}</Alert> : null}

      <div className="grid gap-4 lg:grid-cols-[minmax(0,1fr)_340px]">
        <div className="flex flex-col gap-4">
          <Card>
            <CardBody className="gap-3">
              <SectionTitle>사진</SectionTitle>
              <InventoryImages inventoryId={inventory.id} canUpload={editable} />
            </CardBody>
          </Card>

          <Card>
            <CardBody className="gap-3">
              <SectionTitle aside={<Badge variant="neutral">{categoryName}</Badge>}>
                재고 정보
              </SectionTitle>
              <dl className="border-border border-[2.5px]">
                <DetailRow label="이름" override={inventory.nameOverride !== null}>
                  {inventory.displayName}
                </DetailRow>
                <DetailRow label="가격" override={inventory.priceAmountOverride !== null}>
                  {formatPrice(inventory.priceAmount)}
                </DetailRow>
                <DetailRow label="카테고리" override={inventory.categoryIdOverride !== null}>
                  {categoryName}
                </DetailRow>
                <DetailRow label="설명" override={inventory.descriptionOverride !== null}>
                  {inventory.description?.trim() || '설명 없음'}
                </DetailRow>
                <DetailRow label="위치">{locationPath}</DetailRow>
                <DetailRow label="내 권한">
                  {permissionLabel(inventory.effectivePermission)}
                </DetailRow>
              </dl>
              <div className="flex flex-wrap gap-2">
                <Button asChild variant="secondary">
                  <Link to={`/items/${inventory.itemId}`}>
                    <Package aria-hidden="true" className="size-4" />
                    물건 상세
                  </Link>
                </Button>
                {editable ? (
                  <Button asChild variant="secondary">
                    <Link to={`/inventory/${inventory.id}/edit`}>편집</Link>
                  </Button>
                ) : null}
              </div>
            </CardBody>
          </Card>

          {!inventory.isShared ? <MovementTimeline inventory={inventory} /> : null}
        </div>

        <div className="flex flex-col gap-4">
          <Card>
            <CardBody className="gap-3">
              <SectionTitle>상태</SectionTitle>
              <select
                aria-label="재고 상태"
                value={statusDraft}
                disabled={!editable || updateMutation.isPending}
                onChange={(event) => {
                  const next = event.target.value as InventoryStatus
                  setStatusDraftOverride(next)
                  if (next === 'DISPOSED') setConfirmDisposeOpen(true)
                }}
                className="border-border bg-card h-12 border-[2.5px] px-3 text-sm font-bold disabled:opacity-50"
              >
                {statusOptions.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
              {editable ? (
                <Button
                  type="button"
                  disabled={!statusChanged || updateMutation.isPending}
                  onClick={() => {
                    if (statusDraft === 'DISPOSED') {
                      setConfirmDisposeOpen(true)
                      return
                    }
                    updateMutation.mutate(statusDraft)
                  }}
                >
                  {updateMutation.isPending ? '저장 중…' : '상태 저장'}
                </Button>
              ) : (
                <p className="text-foreground/60 text-xs">상태를 바꿀 권한이 없습니다.</p>
              )}
            </CardBody>
          </Card>

          <Card>
            <CardBody className="gap-2">
              <SectionTitle>위치</SectionTitle>
              <p className="text-xs font-medium">{locationPath}</p>
              {manageable ? (
                <Button type="button" variant="secondary" onClick={() => setMoveOpen(true)}>
                  위치 이동
                </Button>
              ) : null}
            </CardBody>
          </Card>

          {manageable ? (
            <Card>
              <CardBody className="gap-2">
                <SectionTitle>관리</SectionTitle>
                <Button
                  type="button"
                  variant="secondary"
                  className="border-danger text-danger"
                  onClick={() => setDeleteOpen(true)}
                >
                  <Trash2 aria-hidden="true" className="size-4" />
                  재고 삭제
                </Button>
                <p className="text-foreground/60 text-[11px]">
                  삭제해도 다른 재고와 물건 정보는 유지됩니다.
                </p>
              </CardBody>
            </Card>
          ) : null}
        </div>
      </div>

      <ConfirmDialog
        open={confirmDisposeOpen}
        onOpenChange={(next) => {
          setConfirmDisposeOpen(next)
          if (!next) setStatusDraftOverride(null)
        }}
        title="폐기로 바꿀까요?"
        description="폐기한 재고는 기본 목록과 검색에서 숨겨집니다. 언제든 되돌릴 수 있어요."
        confirmLabel="폐기로 변경"
        pending={updateMutation.isPending}
        destructive
        onConfirm={() => {
          setConfirmDisposeOpen(false)
          updateMutation.mutate('DISPOSED')
        }}
      />

      <ConfirmDialog
        open={deleteOpen}
        onOpenChange={setDeleteOpen}
        title="재고를 삭제할까요?"
        description={`“${inventory.displayName}” 재고를 삭제합니다. 이 작업은 되돌릴 수 없습니다.`}
        confirmLabel="삭제"
        pending={deleteMutation.isPending}
        destructive
        onConfirm={() => deleteMutation.mutate()}
      />

      {moveOpen ? (
        <MoveInventoryDialog
          inventory={inventory}
          onClose={() => setMoveOpen(false)}
          onMoved={() => setError(null)}
        />
      ) : null}
    </div>
  )
}
