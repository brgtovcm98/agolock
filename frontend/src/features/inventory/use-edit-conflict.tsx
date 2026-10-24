import { useState } from 'react'
import { ApiError } from '@/shared/api/client'
import { Button } from '@/shared/ui/button'
import { mergeFields } from './edit-state'

const fieldLabels: Record<string, string> = {
  name: '이름',
  nameOverride: '재고 이름',
  description: '설명',
  descriptionOverride: '재고 설명',
  categoryId: '카테고리',
  categoryIdOverride: '재고 카테고리',
  priceAmount: '가격',
  priceAmountOverride: '재고 가격',
}

export function useEditConflict<T extends object>(initial: T, initialVersion: number) {
  const [baseline, setBaseline] = useState(initial)
  const [version, setVersion] = useState(initialVersion)
  const [conflicts, setConflicts] = useState<Array<keyof T>>([])
  const [latest, setLatest] = useState<T>(initial)
  const [blocked, setBlocked] = useState(false)
  async function recover(
    cause: unknown,
    draft: T,
    fetchLatest: () => Promise<{ fields: T; version: number }>,
    apply: (fields: T) => void,
  ) {
    if (!(cause instanceof ApiError) || cause.code !== 'VERSION_CONFLICT') return false
    try {
      const result = await fetchLatest()
      const merged = mergeFields(baseline, draft, result.fields)
      setLatest(result.fields)
      setBaseline(result.fields)
      setVersion(result.version)
      setConflicts(merged.conflicts)
      apply(merged.draft)
    } catch {
      setBlocked(true)
    }
    return true
  }
  function choices(
    draft: T,
    applyField: (key: keyof T, value: T[keyof T]) => void,
    formatValue?: (key: keyof T, value: T[keyof T]) => string,
  ) {
    const describe = (key: keyof T, value: T[keyof T]) =>
      value == null
        ? '마스터 값 사용'
        : value === ''
          ? '빈 설명'
          : (formatValue?.(key, value) ?? String(value))
    return conflicts.map((key) => (
      <div
        key={String(key)}
        role="group"
        aria-label={`${fieldLabels[String(key)] ?? String(key)} 충돌`}
        className="flex flex-wrap items-center gap-2"
      >
        <span>{fieldLabels[String(key)] ?? String(key)} 값이 함께 변경되었어요.</span>
        <p className="basis-full text-xs">
          내 입력: {describe(key, draft[key])} · 최신 값: {describe(key, latest[key])}
        </p>
        <Button
          type="button"
          variant="secondary"
          onClick={() => setConflicts(conflicts.filter((field) => field !== key))}
        >
          내 입력 사용
        </Button>
        <Button
          type="button"
          variant="secondary"
          onClick={() => {
            applyField(key, latest[key])
            setConflicts(conflicts.filter((field) => field !== key))
          }}
        >
          최신 값 사용
        </Button>
      </div>
    ))
  }
  return { baseline, version, blocked, unresolved: conflicts.length > 0, recover, choices }
}
