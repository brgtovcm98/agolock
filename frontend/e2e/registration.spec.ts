import { expect, test } from '@playwright/test'

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

const room = {
  id: '018f0000-0000-7000-8000-000000000010',
  name: '거실',
  description: null,
  shelfCount: 0,
  inventoryCount: 0,
  version: 0,
  effectivePermission: 'OWNER',
  isShared: false,
  createdAt: '2026-10-21T00:00:00Z',
  updatedAt: '2026-10-21T00:00:00Z',
}

const item = {
  id: '018f0000-0000-7000-8000-000000000020',
  name: '새 드릴',
  description: null,
  categoryId: null,
  priceAmount: null,
  currency: 'KRW',
  version: 0,
  createdAt: '2026-10-21T00:00:00Z',
  updatedAt: '2026-10-21T00:00:00Z',
  effectivePermission: 'OWNER',
  isShared: false,
}

const inventory = {
  id: '018f0000-0000-7000-8000-000000000030',
  itemId: item.id,
  itemName: item.name,
  displayName: item.name,
  description: null,
  categoryId: null,
  priceAmount: null,
  status: 'STORED',
  location: {
    type: 'ROOM',
    roomId: room.id,
    shelfId: null,
    boxId: null,
    name: room.name,
  },
  nameOverride: null,
  descriptionOverride: null,
  categoryIdOverride: null,
  priceAmountOverride: null,
  version: 0,
  effectivePermission: 'OWNER',
  isShared: false,
  createdAt: '2026-10-21T00:00:00Z',
  updatedAt: '2026-10-21T00:00:00Z',
}

test.use({ viewport: { width: 1280, height: 900 } })

test('위치를 골라 새 물건과 첫 재고를 등록한다', async ({ page }) => {
  let itemBody: Record<string, unknown> | null = null
  let inventoryBody: Record<string, unknown> | null = null

  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({ json: authMe }),
  )
  await page.route('**/api/v1/auth/config', (route) =>
    route.fulfill({
      json: { inviteRequired: false, passwordMinLength: 10, passwordMaxLength: 128 },
    }),
  )
  await page.route('**/api/v1/categories', (route) =>
    route.fulfill({ json: { items: [] } }),
  )
  await page.route('**/api/v1/inventories**', async (route) => {
    if (route.request().method() === 'POST') {
      inventoryBody = route.request().postDataJSON() as Record<string, unknown>
      await route.fulfill({ status: 201, json: inventory })
      return
    }
    await route.fulfill({ json: { items: [], nextCursor: null } })
  })
  await page.route('**/api/v1/items', async (route) => {
    if (route.request().method() === 'POST') {
      itemBody = route.request().postDataJSON() as Record<string, unknown>
      await route.fulfill({ status: 201, json: item })
      return
    }
    await route.fulfill({ json: { items: [], nextCursor: null } })
  })
  await page.route('**/api/v1/rooms?limit=100', (route) =>
    route.fulfill({ json: { items: [room], nextCursor: null } }),
  )
  await page.route('**/api/v1/rooms/018f0000-0000-7000-8000-000000000010/shelves**', (route) =>
    route.fulfill({ json: { items: [], nextCursor: null } }),
  )

  await page.goto('/inventory')
  await expect(page.getByRole('heading', { name: '재고' })).toBeVisible()

  await page.getByRole('button', { name: '등록', exact: true }).click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByRole('heading', { name: '물건 등록' })).toBeVisible()

  await dialog.getByLabel('물건 이름').fill('새 드릴')
  const roomSelect = dialog.getByRole('combobox', { name: '방' })
  await expect(roomSelect.locator('option[value="018f0000-0000-7000-8000-000000000010"]')).toHaveCount(1)
  await roomSelect.selectOption('018f0000-0000-7000-8000-000000000010')
  await dialog.getByRole('button', { name: '등록', exact: true }).click()

  await expect(dialog.getByText('등록했어요.')).toBeVisible()
  await expect(dialog.getByRole('button', { name: /계속 등록/ })).toBeVisible()
  expect(itemBody).toMatchObject({ name: '새 드릴' })
  expect(inventoryBody).toMatchObject({
    itemId: item.id,
    roomId: room.id,
    status: 'STORED',
  })
})
