import { expect, test } from '@playwright/test'

// Run against the actual local backend with INVENTORY_LIVE=1; no intercepted routes.
test('실제 API에서 박스 등록, 빈 설명·0원 저장과 같은 Space 이동을 처리한다', async ({
  page,
  browser,
}) => {
  test.setTimeout(120000)
  test.skip(
    process.env.INVENTORY_LIVE !== '1',
    '실제 API가 준비되면 INVENTORY_LIVE=1로 실행합니다.',
  )
  await page.goto('/login')
  await page.request.get('/api/v1/auth/csrf')
  const csrf = (await page.context().cookies()).find(
    (cookie) => cookie.name === 'XSRF-TOKEN',
  )?.value
  const headers = { 'X-XSRF-TOKEN': decodeURIComponent(csrf ?? '') }
  const signup = await page.request.post('/api/v1/auth/signup', {
    headers,
    data: {
      email: `inventory-${Date.now()}-${test.info().project.name}@example.com`,
      password: 'InventoryLive!2026',
      displayName: '재고 통합검증',
    },
  })
  expect(signup.ok(), await signup.text()).toBeTruthy()
  const create = async (path: string, data: object) => {
    await page.request.get('/api/v1/auth/csrf')
    const token = (await page.context().cookies()).find(
      (cookie) => cookie.name === 'XSRF-TOKEN',
    )?.value
    const response = await page.request.post(`/api/v1${path}`, {
      headers: { 'X-XSRF-TOKEN': decodeURIComponent(token ?? '') },
      data,
    })
    expect(response.ok(), await response.text()).toBeTruthy()
    return response.json()
  }
  const room = await create('/rooms', { name: '통합검증 방' })
  const shelf = await create(`/rooms/${room.id}/shelves`, { name: '통합검증 선반' })
  const box = await create(`/shelves/${shelf.id}/boxes`, { name: '통합검증 박스' })
  await page.goto('/inventory')
  await page
    .getByRole('button', { name: /^(물건 등록|등록)$/ })
    .filter({ visible: true })
    .first()
    .click()
  const dialog = page.getByRole('dialog')
  await dialog.getByLabel('물건 이름').fill('실제 API 물건')
  await dialog.getByLabel('방', { exact: true }).selectOption(room.id)
  await dialog.getByLabel('선반', { exact: true }).selectOption(shelf.id)
  await dialog.getByLabel('박스', { exact: true }).selectOption(box.id)
  const created = page.waitForResponse(
    (response) =>
      response.url().endsWith('/api/v1/inventories') && response.request().method() === 'POST',
  )
  await dialog.getByRole('button', { name: '등록', exact: true }).click()
  const response = await created
  expect(response.ok(), await response.text()).toBeTruthy()
  expect(response.request().postDataJSON()).toMatchObject({ boxId: box.id })
  expect(response.request().postDataJSON()).not.toHaveProperty('roomId')
  expect(response.request().postDataJSON()).not.toHaveProperty('shelfId')
  const inventory = await response.json()
  await page.goto(`/inventory/${inventory.id}/edit`)
  const toggleOverride = async (name: RegExp) => {
    const box = page.getByRole('checkbox', { name })
    await box.locator('xpath=ancestor::label').click()
    await expect(box).toBeChecked()
  }
  await toggleOverride(/설명/)
  await page.getByLabel('재고 설명').fill('')
  await toggleOverride(/가격/)
  await page.getByLabel('재고 가격').fill('0')
  await page.getByRole('button', { name: '저장', exact: true }).click()
  await expect(page).toHaveURL(`/inventory/${inventory.id}`)
  const latest = await (await page.request.get(`/api/v1/inventories/${inventory.id}`)).json()
  expect(latest.descriptionOverride).toBe('')
  expect(latest.priceAmountOverride).toBe(0)
  await page.getByRole('button', { name: '위치 이동', exact: true }).click()
  await page.getByLabel('대상 위치').selectOption(room.id)
  await page.getByRole('button', { name: '이동', exact: true }).click()
  await expect(page.getByRole('dialog')).toHaveCount(0)
  const moved = await (await page.request.get(`/api/v1/inventories/${inventory.id}`)).json()
  expect(moved.location.type).toBe('ROOM')
  expect(moved.location.roomId).toBe(room.id)
  const sharedContext = await browser.newContext({ baseURL: new URL(page.url()).origin })
  try {
    const sharedPage = await sharedContext.newPage()
    const recipient = `manage-${Date.now()}-${test.info().project.name}@example.com`
    const sharedPost = async (path: string, data?: object) => {
      await sharedPage.request.get('/api/v1/auth/csrf')
      const token = (await sharedContext.cookies()).find(
        (cookie) => cookie.name === 'XSRF-TOKEN',
      )?.value
      return sharedPage.request.post(`/api/v1${path}`, {
        headers: { 'X-XSRF-TOKEN': decodeURIComponent(token ?? '') },
        data,
      })
    }
    const account = await sharedPost('/auth/signup', {
      email: recipient,
      password: 'InventoryLive!2026',
      displayName: '공유 통합검증',
    })
    expect(account.ok(), await account.text()).toBeTruthy()
    await page.request.get('/api/v1/auth/csrf')
    const ownerToken = (await page.context().cookies()).find(
      (cookie) => cookie.name === 'XSRF-TOKEN',
    )?.value
    const invitation = await page.request.post('/api/v1/share-invitations', {
      headers: { 'X-XSRF-TOKEN': decodeURIComponent(ownerToken ?? '') },
      data: { roomId: room.id, invitedEmail: recipient, permission: 'MANAGE' },
    })
    expect(invitation.ok(), await invitation.text()).toBeTruthy()
    const mailpit = process.env.MAILPIT_URL ?? 'http://127.0.0.1:8025'
    let invitationToken = ''
    await expect
      .poll(
        async () => {
          const messagesResponse = await page.request.get(`${mailpit}/api/v1/messages`)
          expect(messagesResponse.ok()).toBeTruthy()
          const mailbox = await messagesResponse.json()
          const message = mailbox.messages.find(
            (message: { To: Array<{ Address: string }>; Subject: string }) =>
              message.Subject.includes('공유 초대') &&
              message.To.some((to) => to.Address === recipient),
          )
          if (!message) return false
          const detail = await (
            await page.request.get(`${mailpit}/api/v1/message/${message.ID}`)
          ).json()
          const link = `${detail.Text} ${detail.HTML}`.match(/[#?&]token=([^&\s"<>]+)/)
          invitationToken = link ? decodeURIComponent(link[1]) : ''
          return Boolean(invitationToken)
        },
        { timeout: 45000, message: '실제 초대 이메일의 수락 token을 기다립니다.' },
      )
      .toBeTruthy()
    const accepted = await sharedPost('/share-invitations/accept', { token: invitationToken })
    expect(accepted.ok(), await accepted.text()).toBeTruthy()
    await sharedPage.goto(`/items/${inventory.itemId}`)
    await sharedPage.getByRole('button', { name: '재고 추가', exact: true }).click()
    const sharedDialog = sharedPage.getByRole('dialog')
    await expect(sharedDialog.getByRole('tab', { name: '새 물건' })).toBeDisabled()
    await sharedDialog.getByLabel('대상 위치').selectOption(box.id)
    const sharedCreated = sharedPage.waitForResponse(
      (response) =>
        response.url().endsWith('/api/v1/inventories') && response.request().method() === 'POST',
    )
    await sharedDialog.getByRole('button', { name: '등록', exact: true }).click()
    const sharedResponse = await sharedCreated
    expect(sharedResponse.ok(), await sharedResponse.text()).toBeTruthy()
    const sharedInventory = await sharedResponse.json()
    await sharedPage.goto(`/inventory/${sharedInventory.id}`)
    await sharedPage.getByRole('button', { name: '위치 이동', exact: true }).click()
    await sharedPage.getByLabel('대상 위치').selectOption(shelf.id)
    await sharedPage.getByRole('button', { name: '이동', exact: true }).click()
    await expect(sharedPage.getByRole('dialog')).toHaveCount(0)
    const sharedMoved = await (
      await sharedPage.request.get(`/api/v1/inventories/${sharedInventory.id}`)
    ).json()
    expect(sharedMoved.location.type).toBe('SHELF')
    expect(sharedMoved.location.shelfId).toBe(shelf.id)
  } finally {
    await sharedContext.close()
  }
})
