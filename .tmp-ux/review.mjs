import { chromium } from '../node_modules/.pnpm/playwright@1.64.1/node_modules/playwright/index.mjs'

const authMe = {
  user: {
    id: '018f0000-0000-7000-8000-000000000001',
    email: 'jimin@example.com',
    displayName: '지민',
    status: 'ACTIVE',
  },
  space: {
    id: '018f0000-0000-7000-8000-000000000002',
    name: '지민의 Space',
    ownerUserId: '018f0000-0000-7000-8000-000000000001',
  },
}

const categories = [
  { id: 'c1', name: '공구', color: null, sortOrder: 0 },
  { id: 'c2', name: '캠핑', color: null, sortOrder: 1 },
]

const items = [
  { id: 'i1', name: '전동 드릴 18V', description: null, categoryId: 'c1', priceAmount: 129000, currency: 'KRW', version: 1, createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z' },
  { id: 'i2', name: '드라이버 세트', description: null, categoryId: 'c1', priceAmount: 32000, currency: 'KRW', version: 1, createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z' },
  { id: 'i3', name: '캠핑 랜턴', description: null, categoryId: 'c2', priceAmount: null, currency: 'KRW', version: 1, createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z' },
]

const inventories = [
  { id: 'v1', itemId: 'i1', itemName: '전동 드릴 18V', displayName: '전동 드릴 18V', description: null, categoryId: 'c1', priceAmount: 129000, status: 'IN_USE', location: { type: 'BOX', roomId: 'r1', shelfId: 's1', boxId: 'b1', name: '파란 박스' }, nameOverride: null, descriptionOverride: null, categoryIdOverride: null, priceAmountOverride: null, version: 1, effectivePermission: 'OWNER', isShared: false, createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z' },
  { id: 'v2', itemId: 'i1', itemName: '전동 드릴 18V', displayName: '전동 드릴 18V', description: null, categoryId: 'c1', priceAmount: 129000, status: 'STORED', location: { type: 'SHELF', roomId: 'r1', shelfId: 's2', boxId: null, name: '베란다 선반 2단' }, nameOverride: null, descriptionOverride: null, categoryIdOverride: null, priceAmountOverride: null, version: 1, effectivePermission: 'OWNER', isShared: false, createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z' },
  { id: 'v3', itemId: 'i2', itemName: '드라이버 세트', displayName: '드라이버 세트', description: null, categoryId: 'c1', priceAmount: 32000, status: 'STORED', location: { type: 'ROOM', roomId: 'r1', shelfId: null, boxId: null, name: '거실' }, nameOverride: null, descriptionOverride: null, categoryIdOverride: null, priceAmountOverride: null, version: 1, effectivePermission: 'OWNER', isShared: false, createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z' },
]

const rooms = [
  { id: 'r1', name: '거실' },
  { id: 'r2', name: '침실' },
]

const json = (body) => ({ status: 200, contentType: 'application/json', body: JSON.stringify(body) })

const browser = await chromium.launch()

for (const [name, viewport] of [
  ['mobile', { width: 390, height: 844 }],
  ['desktop', { width: 1280, height: 900 }],
]) {
  const context = await browser.newContext({ viewport, deviceScaleFactor: 1 })
  const page = await context.newPage()
  await page.route('**/api/v1/auth/me', (route) => route.fulfill(json(authMe)))
  await page.route('**/api/v1/auth/config', (route) =>
    route.fulfill(json({ inviteRequired: false, passwordMinLength: 10, passwordMaxLength: 128 })),
  )
  await page.route('**/api/v1/categories', (route) => route.fulfill(json({ items: categories })))
  await page.route('**/api/v1/items?limit=100', (route) => route.fulfill(json({ items })))
  await page.route('**/api/v1/inventories', (route) => route.fulfill(json({ items: inventories })))
  await page.route('**/api/v1/rooms?limit=100', (route) => route.fulfill(json({ items: rooms })))
  await page.route('**/api/v1/rooms/r1', (route) =>
    route.fulfill(json({ id: 'r1', name: '거실', description: '메인 수납 공간', shelfCount: 2, version: 1, effectivePermission: 'OWNER', isShared: false, createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z' })),
  )
  await page.route('**/api/v1/rooms/r1/shelves', (route) =>
    route.fulfill(json({ items: [
      { id: 's1', roomId: 'r1', name: '공구함', description: null, boxCount: 3, version: 1, effectivePermission: 'OWNER', isShared: false, createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z' },
      { id: 's2', roomId: 'r1', name: '베란다 선반 2단', description: null, boxCount: 0, version: 1, effectivePermission: 'OWNER', isShared: false, createdAt: '2026-10-01T00:00:00Z', updatedAt: '2026-10-01T00:00:00Z' },
    ] })),
  )

  await page.goto('http://127.0.0.1:5173/inventory', { waitUntil: 'networkidle' })
  await page.screenshot({ path: `.tmp-ux/inventory-${name}.png`, fullPage: true })

  await page.goto('http://127.0.0.1:5173/', { waitUntil: 'networkidle' })
  await page.screenshot({ path: `.tmp-ux/home-${name}.png`, fullPage: true })

  await page.goto('http://127.0.0.1:5173/space/rooms/r1', { waitUntil: 'networkidle' })
  await page.screenshot({ path: `.tmp-ux/room-${name}.png`, fullPage: true })

  await context.close()
}

await browser.close()
