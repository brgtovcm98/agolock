export type AppPermission = 'NONE' | 'VIEW' | 'EDIT' | 'MANAGE' | 'OWNER'

const labels: Record<AppPermission, string> = {
  NONE: '권한 없음',
  VIEW: '보기',
  EDIT: '편집',
  MANAGE: '관리',
  OWNER: '소유자',
}

const descriptions: Record<AppPermission, string> = {
  NONE: '접근할 수 없습니다.',
  VIEW: '물건과 위치를 볼 수 있습니다.',
  EDIT: '재고의 상태와 사진을 바꿀 수 있습니다.',
  MANAGE: '재고를 추가·이동·삭제할 수 있습니다.',
  OWNER: '물건과 공간을 모두 관리할 수 있습니다.',
}

function normalize(permission: string | null | undefined): AppPermission {
  return permission && permission in labels ? (permission as AppPermission) : 'NONE'
}

export function permissionLabel(permission: string | null | undefined) {
  return labels[normalize(permission)]
}

export function permissionDescription(permission: string | null | undefined) {
  return descriptions[normalize(permission)]
}

export function canEdit(permission: string | null | undefined) {
  return ['EDIT', 'MANAGE', 'OWNER'].includes(normalize(permission))
}

export function canManage(permission: string | null | undefined) {
  return ['MANAGE', 'OWNER'].includes(normalize(permission))
}

export function isOwner(permission: string | null | undefined) {
  return normalize(permission) === 'OWNER'
}
