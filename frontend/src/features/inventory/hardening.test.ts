import { afterEach, expect, it, vi } from 'vitest'
import { inventoryApi } from './api'

afterEach(() => vi.unstubAllGlobals())
it('serializes only deepest location and retains the retry key', async () => {
  document.cookie = 'XSRF-TOKEN=test'
  const requests: RequestInit[] = []
  vi.stubGlobal(
    'fetch',
    vi.fn(async (_url, init) => {
      requests.push(init)
      return new Response('{}', { headers: { 'Content-Type': 'application/json' } })
    }),
  )
  const input = {
    itemId: 'item',
    roomId: 'room',
    shelfId: 'shelf',
    boxId: 'box',
    idempotencyKey: 'same',
  }
  await inventoryApi.createInventory(input)
  await inventoryApi.createInventory(input)
  expect(JSON.parse(String(requests[0].body))).toEqual({
    itemId: 'item',
    newItem: null,
    boxId: 'box',
    status: 'STORED',
  })
  expect(new Headers(requests[1].headers).get('Idempotency-Key')).toBe('same')
})
it('sends sparse preview by POST, versioned delete and paged contextual queries', async () => {
  document.cookie = 'XSRF-TOKEN=test'
  const fetcher = vi.fn(
    async () => new Response('{}', { headers: { 'Content-Type': 'application/json' } }),
  )
  vi.stubGlobal('fetch', fetcher)
  await inventoryApi.itemUpdateImpact('i', { version: 2, description: '' })
  expect(fetcher).toHaveBeenLastCalledWith(
    '/api/v1/items/i/update-impact',
    expect.objectContaining({ method: 'POST', body: '{"version":2,"description":""}' }),
  )
  await inventoryApi.deleteInventory('v', 2)
  expect(fetcher).toHaveBeenLastCalledWith(
    '/api/v1/inventories/v?version=2',
    expect.objectContaining({ method: 'DELETE' }),
  )
  await inventoryApi.categories({ itemId: 'i', limit: 100, cursor: 'next' })
  expect(fetcher).toHaveBeenLastCalledWith(
    '/api/v1/categories?itemId=i&limit=100&cursor=next',
    expect.anything(),
  )
})

it('moves and filters with only the deepest selected location', async () => {
  document.cookie = 'XSRF-TOKEN=test'
  const fetcher = vi.fn(
    async () => new Response('{}', { headers: { 'Content-Type': 'application/json' } }),
  )
  vi.stubGlobal('fetch', fetcher)
  await inventoryApi.moveInventory('v', { version: 2, roomId: 'r', shelfId: 's' })
  expect(fetcher).toHaveBeenLastCalledWith(
    '/api/v1/inventories/v/location',
    expect.objectContaining({ body: '{"version":2,"shelfId":"s"}' }),
  )
  await inventoryApi.inventories({ roomId: 'r', shelfId: 's', boxId: 'b' })
  expect(fetcher).toHaveBeenLastCalledWith(
    '/api/v1/inventories?boxId=b&limit=20',
    expect.anything(),
  )
  await inventoryApi.itemInventories('i', { status: 'DISPOSED', limit: 100, cursor: 'next' })
  expect(fetcher).toHaveBeenLastCalledWith(
    '/api/v1/items/i/inventories?status=DISPOSED&limit=100&cursor=next',
    expect.anything(),
  )
})

it('collects category pages beyond the first 100', async () => {
  const { collectPages } = await import('./api')
  const cursors: Array<string | undefined> = []
  const result = await collectPages(async (cursor) => {
    cursors.push(cursor)
    return cursor
      ? { items: [100], nextCursor: null }
      : { items: Array.from({ length: 100 }, (_, index) => index), nextCursor: 'page2' }
  })
  expect(cursors).toEqual([undefined, 'page2'])
  expect(result.items).toHaveLength(101)
  expect(result.items[100]).toBe(100)
})
