import { expect, it } from 'vitest'
import { mergeFields, sparsePatch, parsePrice } from './edit-state'
it('keeps omission, empty description, inheritance null and zero distinct', () => {
  expect(
    sparsePatch(
      { description: 'old', price: 2, category: 'c' },
      { description: '', price: 0, category: null },
    ),
  ).toEqual({ description: '', price: 0, category: null })
  expect(sparsePatch({ description: '' }, { description: '' })).toEqual({})
  expect(parsePrice('2147483647')).toBe(2147483647)
  expect(() => parsePrice('2147483648')).toThrow()
  expect(() => parsePrice('1.5')).toThrow()
})
it('merges nonoverlapping edits and requires choices only for different changes', () => {
  const result = mergeFields(
    { a: 'a', b: 'b', c: 'c' },
    { a: 'mine', b: 'b', c: 'mine' },
    { a: 'a', b: 'latest', c: 'theirs' },
  )
  expect(result.draft).toEqual({ a: 'mine', b: 'latest', c: 'mine' })
  expect(result.conflicts).toEqual(['c'])
})
