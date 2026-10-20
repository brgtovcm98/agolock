import type { InventoryStatus } from '@/shared/ui/status-tag'
import type { ThumbnailTone } from '@/shared/ui/thumbnail'

export type PreviewInventory = {
  id: string
  location: string
  status: InventoryStatus
  owner?: string
}

export type PreviewItem = {
  id: string
  name: string
  location: string
  status: InventoryStatus
  tone: ThumbnailTone
  price: number
  category: string
  description: string
  overrides: Array<'price' | 'description'>
  inventories: PreviewInventory[]
}

export const previewItems: PreviewItem[] = [
  {
    id: 'item-drill',
    name: '전동 드릴 18V',
    location: '거실 › 공구함 › 파란 박스',
    status: 'IN_USE',
    tone: 'c1',
    price: 129_000,
    category: '공구',
    description: '배터리 2개 포함, 왼쪽 날 마모',
    overrides: ['price', 'description'],
    inventories: [
      { id: 'inv-1', location: '베란다 선반 2단', status: 'STORED' },
      { id: 'inv-2', location: '민지네 창고', status: 'STORED', owner: '민지' },
      { id: 'inv-3', location: '창고 › 케이블 박스', status: 'STORED' },
    ],
  },
  {
    id: 'item-driver',
    name: '드라이버 세트',
    location: '거실 › 공구함',
    status: 'STORED',
    tone: 'c3',
    price: 24_000,
    category: '공구',
    description: '십자·일자 12종',
    overrides: [],
    inventories: [{ id: 'inv-4', location: '거실 › 공구함', status: 'STORED' }],
  },
  {
    id: 'item-tent',
    name: '캠핑 랜턴',
    location: '창고 › 캠핑 박스',
    status: 'STORED',
    tone: 'c2',
    price: 38_000,
    category: '캠핑',
    description: '충전식, 최대 12시간',
    overrides: [],
    inventories: [{ id: 'inv-5', location: '창고 › 캠핑 박스', status: 'STORED' }],
  },
]

export const drillItem = previewItems[0]
export const drillInventory = drillItem.inventories[0]

export const recentSearches = ['캠핑 랜턴', '겨울 이불', '여권']

export const receivedShares = [
  {
    name: '민지네 베란다',
    permission: '편집 권한',
    expires: '만료 D-6',
    status: '수락 대기',
  },
]

export const filterGroups = [
  {
    title: '상태',
    items: [
      { label: '사용중', count: 3, checked: true },
      { label: '보관', count: 5, checked: true },
      { label: '폐기 포함', count: 1, checked: false },
    ],
  },
  {
    title: '위치',
    items: [
      { label: '내 공간', count: 7, checked: true },
      { label: '민지네 베란다', count: 1, checked: true },
    ],
  },
  {
    title: '카테고리',
    items: [
      { label: '공구', count: 6, checked: false },
      { label: '캠핑', count: 2, checked: false },
    ],
  },
  {
    title: '기타',
    items: [
      { label: '사진 있음', count: 4, checked: false },
      { label: '공유받은 것만', count: 1, checked: false },
    ],
  },
]

export type SpaceNode = {
  name: string
  meta: string
  tone: 'room' | 'shelf' | 'box' | 'shared'
  badge?: string
  selected?: boolean
  children?: SpaceNode[]
}

export const spaceTree: SpaceNode[] = [
  {
    name: '거실',
    meta: '선반 3 · 재고 24',
    tone: 'room' as const,
    children: [
      {
        name: '책장',
        meta: '박스 2 · 재고 11',
        tone: 'shelf' as const,
        children: [
          { name: '파란 박스', meta: '재고 6', tone: 'box' as const, selected: true },
          { name: '케이블 박스', meta: '재고 5', tone: 'box' as const },
        ],
      },
      { name: '공구함', meta: '재고 8', tone: 'shelf' as const },
    ],
  },
  { name: '안방', meta: '선반 2 · 재고 15', tone: 'room' as const },
  { name: '창고', meta: '선반 4 · 재고 38', tone: 'room' as const },
  {
    name: '민지네 베란다',
    meta: '편집 · 공유 선반 1',
    tone: 'shared' as const,
    badge: '민지 소유',
  },
]

export const boxGrid = [
  { name: '파란 박스', count: 6, tone: 'c2' as ThumbnailTone },
  { name: '케이블 박스', count: 5, tone: 'c2' as ThumbnailTone },
  { name: '공구함', count: 8, tone: 'c1' as ThumbnailTone },
]

export const movementHistory = [
  { date: '2026.09.30', text: '공구함 → 파란 박스' },
  { date: '2026.06.12', text: '창고 → 공구함' },
  { date: '2026.03.02', text: '최초 등록' },
]

export const formatWon = (value: number) => `₩${value.toLocaleString('ko-KR')}`
