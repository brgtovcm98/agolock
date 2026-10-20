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

test('로그인 후 M1 홈과 설정으로 이동한다', async ({ page }) => {
  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        title: '인증이 필요합니다',
        status: 401,
        code: 'AUTHENTICATION_REQUIRED',
        detail: '로그인이 필요합니다.',
      }),
    }),
  )
  await page.route('**/api/v1/auth/config', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        inviteRequired: false,
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
  await page.route('**/api/v1/auth/login', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(authMe),
    }),
  )

  await page.goto('/login')
  await expect(page.getByRole('heading', { name: '다시 만났네요' })).toBeVisible()

  await page.getByLabel('이메일').fill('jimin@example.com')
  await page.getByLabel('비밀번호', { exact: true }).fill('password-1234')
  await page.getByRole('button', { name: '로그인' }).click()

  await expect(page).toHaveURL('/')
  await expect(page.getByRole('heading', { name: '지민님, 반가워요' })).toBeVisible()

  await page.getByRole('link', { name: '설정 열기' }).click()
  await expect(page.getByRole('heading', { name: '설정' })).toBeVisible()
  await expect(page.getByLabel('표시 이름')).toHaveValue('지민')
  await expect(page.getByLabel('Space 이름')).toHaveValue('지민의 Space')
})

test('디자인 미리보기는 공개 라우트에서 열린다', async ({ page }) => {
  await page.route('**/api/v1/auth/me', (route) =>
    route.fulfill({
      status: 401,
      contentType: 'application/problem+json',
      body: JSON.stringify({
        title: '인증이 필요합니다',
        status: 401,
        code: 'AUTHENTICATION_REQUIRED',
      }),
    }),
  )
  await page.route('**/api/v1/auth/config', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ inviteRequired: true, passwordMinLength: 10, passwordMaxLength: 128 }),
    }),
  )

  await page.goto('/design-preview?screen=home&mode=mobile')

  await expect(page.getByText('E안 디자인 미리보기')).toBeVisible()
  await expect(page.getByText('전동 드릴 18V').first()).toBeVisible()

  await page.getByRole('button', { name: /공간 트리/ }).click()
  await expect(page.getByRole('heading', { name: '공간 지도' })).toBeVisible()

  await page.getByRole('button', { name: /재고 상세/ }).click()
  await expect(page.getByText('같은 물건 재고 3개 중 1번')).toBeVisible()
})
