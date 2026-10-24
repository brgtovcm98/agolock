import { expect, test } from '@playwright/test'

const authMe = {
  user: {
    id: '018f0000-0000-7000-8000-000000000001',
    email: 'jimin@example.com',
    displayName: '지민',
    status: 'ACTIVE',
    emailVerified: true,
  },
  space: {
    id: '018f0000-0000-7000-8000-000000000002',
    name: '지민의 Space',
    ownerUserId: '018f0000-0000-7000-8000-000000000001',
  },
}

const inventory = {
  id: 'inventory-1',
  itemId: 'item-1',
  itemName: '책',
  displayName: '책',
  description: null,
  categoryId: null,
  priceAmount: null,
  status: 'STORED',
  location: { type: 'ROOM', roomId: 'room-1', shelfId: null, boxId: null, name: '거실' },
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

test('Inventory 이미지를 업로드하고 READY 갤러리에 표시한다', async ({ page }) => {
  let completed = false
  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(authMe) }),
  )
  await page.route('**/api/v1/auth/config', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        inviteRequired: false,
        invitationEmailDiscoveryEnabled: false,
        passwordMinLength: 10,
        passwordMaxLength: 128,
      }),
    }),
  )
  await page.route('**/api/v1/auth/csrf', (route) =>
    route.fulfill({
      status: 200,
      headers: { 'set-cookie': 'XSRF-TOKEN=e2e-csrf; Path=/' },
      contentType: 'application/json',
      body: JSON.stringify({ token: 'e2e-csrf' }),
    }),
  )
  await page.route('**/api/v1/inventories?**', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ items: [inventory], nextCursor: null }),
    }),
  )
  await page.route('**/api/v1/inventories/inventory-1', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(inventory),
    }),
  )
  await page.route('**/api/v1/inventories/inventory-1/images', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: completed
          ? [
              {
                id: 'image-1',
                status: 'READY',
                itemId: null,
                inventoryId: 'inventory-1',
                mimeType: 'image/webp',
                sizeBytes: 12,
                isPrimary: false,
                createdAt: '2026-10-21T00:00:00Z',
              },
            ]
          : [],
      }),
    }),
  )
  await page.route('**/api/v1/images/presign', (route) =>
    route.fulfill({
      status: 201,
      contentType: 'application/json',
      body: JSON.stringify({
        imageId: 'image-1',
        uploadUrl: 'http://upload.local/image-1',
        objectKey: 'spaces/s/inventory/inventory-1/image-1/original.webp',
        expiresAt: '2026-10-21T00:05:00Z',
        uploadExpiresAt: '2026-10-22T00:00:00Z',
      }),
    }),
  )
  await page.route('**/api/v1/images/image-1/complete', (route) => {
    completed = true
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        id: 'image-1',
        status: 'PENDING',
        itemId: null,
        inventoryId: 'inventory-1',
        mimeType: 'image/webp',
        sizeBytes: 12,
        isPrimary: false,
        createdAt: '2026-10-21T00:00:00Z',
      }),
    })
  })
  await page.route('**/api/v1/images/image-1/url', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ url: 'http://image.local/thumb.webp' }),
    }),
  )
  await page.route('http://upload.local/**', (route) => route.fulfill({ status: 200, body: '' }))

  await page.goto('/inventory')
  await expect(page.getByRole('heading', { name: '재고' })).toBeVisible()
  await page.getByRole('link', { name: /책/ }).click()
  await expect(page.getByRole('heading', { name: '책' })).toBeVisible()
  await page.getByRole('button', { name: '사진 추가' }).click()
  await page.locator('input[type=file]').setInputFiles({
    name: 'photo.webp',
    mimeType: 'image/webp',
    buffer: Buffer.from('RIFF0000WEBP'),
  })
  await expect(page.getByText('준비됨').first()).toBeVisible()
})
