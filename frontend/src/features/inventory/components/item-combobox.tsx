import { useQuery } from '@tanstack/react-query'
import { Search } from 'lucide-react'
import { useEffect, useState } from 'react'
import { inventoryApi, type Item } from '../api'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { formatPrice } from '@/shared/lib/format'

export function ItemCombobox({
  value,
  onChange,
  disabled = false,
}: {
  value: Item | null
  onChange: (item: Item | null) => void
  disabled?: boolean
}) {
  const [query, setQuery] = useState('')
  const [debounced, setDebounced] = useState('')

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(query.trim()), 250)
    return () => window.clearTimeout(timer)
  }, [query])

  const recentQuery = useQuery({
    queryKey: ['item', 'search', 'recent'],
    queryFn: () => inventoryApi.items({ limit: 6, scope: 'ALL' }),
    enabled: !value && !debounced,
  })
  const searchQuery = useQuery({
    queryKey: ['item', 'search', debounced],
    queryFn: () => inventoryApi.items({ q: debounced, limit: 12, scope: 'ALL' }),
    enabled: !value && debounced.length > 0,
  })

  const results = debounced ? (searchQuery.data?.items ?? []) : (recentQuery.data?.items ?? [])
  const isLoading = debounced ? searchQuery.isLoading : recentQuery.isLoading
  const isError = debounced ? searchQuery.isError : recentQuery.isError

  if (value) {
    return (
      <div className="border-border bg-card flex items-center gap-3 border-[2.5px] p-3">
        <div className="min-w-0 flex-1">
          <p className="truncate text-sm font-black">{value.name}</p>
          <p className="text-foreground/60 text-[11px]">{formatPrice(value.priceAmount)}</p>
        </div>
        <Button type="button" size="sm" variant="secondary" onClick={() => onChange(null)}>
          변경
        </Button>
      </div>
    )
  }

  return (
    <div className="flex flex-col gap-2">
      <label className="flex flex-col gap-1.5">
        <span className="text-xs font-black">물건 검색</span>
        <span className="border-border bg-card flex items-center border-[2.5px]">
          <Search aria-hidden="true" className="text-foreground/50 ml-3 size-4 shrink-0" />
          <input
            value={query}
            disabled={disabled}
            onChange={(event) => setQuery(event.target.value)}
            placeholder="이름으로 찾기"
            autoComplete="off"
            className="h-12 min-w-0 flex-1 bg-transparent px-2 text-sm font-medium outline-none"
          />
        </span>
      </label>

      {isError ? <Alert variant="error">물건을 불러오지 못했습니다.</Alert> : null}
      {isLoading ? (
        <p role="status" className="text-xs">
          찾는 중…
        </p>
      ) : null}
      {!isLoading && results.length === 0 ? (
        <p className="text-foreground/60 text-xs">
          {debounced ? '검색 결과가 없습니다.' : '아직 등록된 물건이 없습니다.'}
        </p>
      ) : null}
      {results.length > 0 ? (
        <div className="flex flex-col gap-1.5">
          {!debounced ? <p className="label-caps text-foreground/60">최근 물건</p> : null}
          <ul className="flex max-h-56 flex-col gap-1.5 overflow-y-auto">
            {results.map((item) => (
              <li key={item.id}>
                <button
                  type="button"
                  onClick={() => onChange(item)}
                  className="border-border bg-card hover:bg-sunken flex w-full items-center justify-between gap-2 border-2 px-3 py-2 text-left"
                >
                  <span className="truncate text-sm font-black">{item.name}</span>
                  <span className="text-foreground/60 shrink-0 text-[11px]">
                    {formatPrice(item.priceAmount)}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </div>
      ) : null}
    </div>
  )
}
