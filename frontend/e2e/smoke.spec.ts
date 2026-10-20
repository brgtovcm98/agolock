import { expect, test } from '@playwright/test'

test('M0 홈과 디자인 미리보기가 열린다', async ({ page }) => {
  await page.goto('/')

  await expect(page.getByRole('heading', { name: '기반이 준비됐어요' })).toBeVisible()
  await expect(page.getByText('M0에서 확인한 것')).toBeVisible()

  await page.getByRole('link', { name: '디자인 미리보기' }).click()

  await expect(page.getByText('E안 디자인 미리보기')).toBeVisible()
  await expect(page.getByText('전동 드릴 18V').first()).toBeVisible()
  await expect(page.getByRole('button', { name: '모바일 390' })).toHaveAttribute(
    'aria-pressed',
    'true',
  )

  await page.getByRole('button', { name: /공간 트리/ }).click()
  await expect(page.getByRole('heading', { name: '공간 지도' })).toBeVisible()

  await page.getByRole('button', { name: /재고 상세/ }).click()
  await expect(page.getByText('같은 물건 재고 3개 중 1번')).toBeVisible()
})
