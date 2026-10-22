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

test('검색 결과를 물건 그룹과 재고로 보여준다', async ({ page }) => {
  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(authMe) }),
  )
  await page.route('**/api/v1/search**', (route) => {
    const url = new URL(route.request().url())
    if (url.pathname.endsWith('/search/recent')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [] }),
      })
    }
    if (url.pathname.endsWith('/search/frequent')) {
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ items: [] }),
      })
    }
    return route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items: [
          {
            itemId: '018f0000-0000-7000-8000-000000000011',
            itemName: '무선 이어폰',
            matchedInventoryCount: 2,
            inventoryCount: 2,
            hasMoreInventories: false,
            primaryImageId: null,
            effectiveCategory: null,
            inventories: [
              {
                inventoryId: '018f0000-0000-7000-8000-000000000021',
                effectiveName: '무선 이어폰',
                status: 'STORED',
                locationPath: '거실 > 서랍',
                shared: false,
                ownerName: null,
              },
              {
                inventoryId: '018f0000-0000-7000-8000-000000000022',
                effectiveName: '무선 이어폰(여행용)',
                status: 'IN_USE',
                locationPath: '침실 > 책상',
                shared: true,
                ownerName: '지민',
              },
            ],
          },
        ],
        nextCursor: null,
      }),
    })
  })

  await page.goto('/search?q=이어폰')

  await expect(page.getByRole('heading', { name: '무엇을 찾으세요?' })).toBeVisible()
  await expect(page.getByRole('link', { name: '무선 이어폰' }).first()).toBeVisible()
  await expect(page.getByText('거실 > 서랍')).toBeVisible()
  await expect(page.getByText('무선 이어폰(여행용)')).toBeVisible()
  await expect(page.getByRole('link', { name: /무선 이어폰\(여행용\).*공유/ })).toBeVisible()
})
