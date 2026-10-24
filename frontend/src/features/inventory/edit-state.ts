import type { QueryClient } from '@tanstack/react-query'
export function sparsePatch<T extends object>(baseline: T, draft: T): Partial<T> {
  return Object.fromEntries(
    Object.entries(draft).filter(([key, value]) => !Object.is(baseline[key as keyof T], value)),
  ) as Partial<T>
}
export function parsePrice(value: string): number | null {
  if (!value.trim()) return null
  const price = Number(value)
  if (!Number.isInteger(price) || price < 0 || price > 2147483647)
    throw new Error('가격은 0원부터 2147483647원까지의 정수로 입력해 주세요.')
  return price
}
export function mergeFields<T extends object>(baseline: T, draft: T, latest: T) {
  const merged = { ...draft }
  const conflicts: Array<keyof T> = []
  for (const key of Object.keys(draft) as Array<keyof T>) {
    if (Object.is(draft[key], baseline[key])) merged[key] = latest[key]
    else if (!Object.is(latest[key], baseline[key]) && !Object.is(draft[key], latest[key]))
      conflicts.push(key)
  }
  return { draft: merged, conflicts }
}
export async function invalidateInventory(client: QueryClient) {
  await Promise.all(
    ['inventory', 'item', 'hierarchy', 'category', 'search'].map((key) =>
      client.invalidateQueries({ queryKey: [key] }),
    ),
  )
}
