import AxeBuilder from '@axe-core/playwright'
import { expect, test, type Page } from '@playwright/test'

const authMe = {
  user: {
    id: 'u1',
    email: 'jimin@example.com',
    displayName: '지민',
    status: 'ACTIVE',
    emailVerified: true,
  },
  space: { id: 'sp1', name: '지민의 Space', ownerUserId: 'u1' },
}

const inventory = {
  id: 'v1',
  itemId: 'i1',
  itemName: '전동 드릴 18V',
  displayName: '전동 드릴 18V',
  description: '배터리 2개 포함',
  categoryId: 'c1',
  priceAmount: 129000,
  status: 'IN_USE',
  location: { type: 'ROOM', roomId: 'r1', shelfId: null, boxId: null, name: '거실' },
  nameOverride: null,
  descriptionOverride: null,
  categoryIdOverride: null,
  priceAmountOverride: null,
  version: 2,
  effectivePermission: 'OWNER',
  isShared: false,
  createdAt: '2026-10-20T09:00:00Z',
  updatedAt: '2026-10-20T09:00:00Z',
}

async function mockApi(page: Page) {
  await page.route('**/api/v1/**', async (route) => {
    const request = route.request()
    const url = new URL(request.url())
    const path = url.pathname.replace('/api/v1', '')
    const method = request.method()
    const json = (body: unknown, status = 200) =>
      route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(body) })
    if (path === '/auth/me') return json(authMe)
    if (path === '/auth/config')
      return json({
        inviteRequired: false,
        invitationEmailDiscoveryEnabled: false,
        passwordMinLength: 10,
        passwordMaxLength: 128,
      })
    if (path === '/categories')
      return json({ items: [{ id: 'c1', name: '공구', color: null, sortOrder: 0 }] })
    if (path === '/inventories' && method === 'GET')
      return json({ items: [inventory], nextCursor: null })
    if (path === '/inventories/v1') return json(inventory)
    if (path === '/inventories/v1/images') return json({ items: [] })
    if (path === '/inventories/v1/movements') return json([])
    if (path === '/items/i1')
      return json({
        id: 'i1',
        name: '전동 드릴 18V',
        description: null,
        categoryId: 'c1',
        priceAmount: 129000,
        currency: 'KRW',
        version: 1,
        createdAt: '2026-10-20T09:00:00Z',
        updatedAt: '2026-10-20T09:00:00Z',
        effectivePermission: 'OWNER',
        isShared: false,
      })
    if (path === '/items/i1/inventories') return json({ items: [inventory], nextCursor: null })
    if (path === '/search')
      return json({
        items: [
          {
            itemId: 'i1',
            itemName: '전동 드릴 18V',
            matchedInventoryCount: 1,
            inventoryCount: 1,
            hasMoreInventories: false,
            primaryImageId: null,
            effectiveCategory: { id: 'c1', name: '공구', color: null },
            inventories: [
              {
                inventoryId: 'v1',
                effectiveName: '전동 드릴 18V',
                status: 'IN_USE',
                locationPath: '거실',
                shared: false,
                ownerName: null,
              },
            ],
          },
        ],
        nextCursor: null,
      })
    if (path === '/search/recent') return json({ items: [] })
    if (path === '/search/frequent') return json({ items: [] })
    if (path === '/rooms') return json({ items: [{ id: 'r1', name: '거실' }] })
    return json({ items: [], nextCursor: null })
  })
}

async function expectNoA11yViolations(page: Page) {
  const results = await new AxeBuilder({ page })
    .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'])
    .analyze()
  expect(
    results.violations.map((violation) => ({
      id: violation.id,
      impact: violation.impact,
      nodes: violation.nodes.map((node) => node.target.join(' ')),
    })),
  ).toEqual([])
}

test('홈 화면 접근성', async ({ page }) => {
  await mockApi(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: /무엇을 찾으세요/ })).toBeVisible()
  await expectNoA11yViolations(page)
})

test('재고 상세 화면 접근성', async ({ page }) => {
  await mockApi(page)
  await page.goto('/inventory/v1')
  await expect(page.getByRole('heading', { name: '전동 드릴 18V' })).toBeVisible()
  await expectNoA11yViolations(page)
})

test('등록 시트 접근성', async ({ page }) => {
  await mockApi(page)
  await page.goto('/')
  await page.getByRole('button', { name: /등록/ }).first().click()
  await expect(page.getByRole('dialog')).toBeVisible()
  await expectNoA11yViolations(page)
})

test('검색 결과 접근성', async ({ page }) => {
  await mockApi(page)
  await page.goto('/search?q=드릴')
  await expect(page.getByRole('heading', { name: '무엇을 찾으세요?' })).toBeVisible()
  await expect(page.getByRole('list', { name: '검색 결과' })).toBeVisible()
  await expectNoA11yViolations(page)
})

test('토스트 접근성', async ({ page }) => {
  await mockApi(page)
  await page.goto('/settings/categories')
  await page.getByLabel('새 카테고리').fill('테스트')
  await page.getByRole('button', { name: '추가' }).click()
  await expect(page.getByText('카테고리를 만들었어요.')).toBeVisible()
  await expectNoA11yViolations(page)
})

test('다크 홈 접근성', async ({ page }) => {
  await page.addInitScript(() => {
    window.localStorage.setItem('agolock.theme', 'dark')
  })
  await mockApi(page)
  await page.goto('/')
  await expect(page.getByRole('heading', { name: /무엇을 찾으세요/ })).toBeVisible()
  await expectNoA11yViolations(page)
})
