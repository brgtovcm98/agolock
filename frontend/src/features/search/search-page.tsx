import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ChevronDown, MapPin, Package } from 'lucide-react'
import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Alert } from '@/shared/ui/alert'
import { Badge } from '@/shared/ui/badge'
import { Button } from '@/shared/ui/button'
import { Card, CardBody } from '@/shared/ui/card'
import { SearchField } from '@/shared/ui/search-field'
import { MetaText, PageTitle, SectionTitle } from '@/shared/ui/typography'
import { searchApi, type SearchFilters, type SearchItemGroup } from './api'

const statusOptions = [
  { value: 'IN_USE', label: '사용 중' },
  { value: 'STORED', label: '보관 중' },
  { value: 'DISPOSED', label: '폐기' },
] as const

const sortOptions = [
  { value: 'RELEVANCE', label: '관련도순' },
  { value: 'UPDATED_DESC', label: '최근 수정순' },
  { value: 'NAME_ASC', label: '이름순' },
  { value: 'VIEWED_DESC', label: '최근 조회순' },
  { value: 'STATUS_ASC', label: '상태순' },
] as const

export function SearchPage() {
  const [params, setParams] = useSearchParams()
  const queryClient = useQueryClient()
  const q = params.get('q') ?? ''
  const [draft, setDraft] = useState(q)
  const [filters, setFilters] = useState<SearchFilters>({
    statuses: [],
    scope: 'ALL',
    sort: 'RELEVANCE',
    hasImage: null,
  })
  const hasSearch = q.trim().length > 0 || filters.statuses.length > 0

  const results = useInfiniteQuery({
    queryKey: ['search', q, filters],
    queryFn: ({ pageParam }) => searchApi.search(q, filters, pageParam),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (last) => last.nextCursor ?? undefined,
    enabled: hasSearch,
  })
  const recent = useQuery({ queryKey: ['search', 'recent'], queryFn: searchApi.recent })
  const frequent = useQuery({ queryKey: ['search', 'frequent'], queryFn: searchApi.frequent })
  const clearRecent = useMutation({
    mutationFn: searchApi.clearRecent,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['search', 'recent'] }),
  })
  const recordView = useMutation({ mutationFn: searchApi.recordView })

  const groups = results.data?.pages.flatMap((page) => page.items) ?? []
  const total = groups.reduce((sum, group) => sum + group.inventoryCount, 0)

  function submit(value: string) {
    const next = value.trim()
    setDraft(next)
    setParams(next ? { q: next } : {})
  }

  function toggleStatus(status: (typeof statusOptions)[number]['value']) {
    setFilters((current) => ({
      ...current,
      statuses: current.statuses.includes(status)
        ? current.statuses.filter((item) => item !== status)
        : [...current.statuses, status],
    }))
  }

  return (
    <div className="flex flex-col gap-6">
      <section className="flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <p className="label-caps text-foreground/60">검색</p>
          <PageTitle>무엇을 찾으세요?</PageTitle>
          <MetaText>
            이름·설명·위치·카테고리로 찾고, 같은 물건의 재고를 한 묶음으로 봅니다.
          </MetaText>
        </div>
        <SearchField
          value={draft}
          placeholder="예: 이어폰, 거실, 공구"
          onChange={setDraft}
          onSubmit={submit}
        />
        <div className="border-border bg-card flex flex-wrap items-center gap-2 border-[2.5px] p-2">
          {statusOptions.map((option) => (
            <button
              key={option.value}
              type="button"
              aria-pressed={filters.statuses.includes(option.value)}
              onClick={() => toggleStatus(option.value)}
              className={`border-border h-11 border-2 px-3 text-xs font-black transition-colors duration-150 ${
                filters.statuses.includes(option.value) ? 'bg-primary text-on-accent' : 'bg-card'
              }`}
            >
              {option.label}
            </button>
          ))}
          <select
            aria-label="검색 범위"
            value={filters.scope}
            onChange={(event) =>
              setFilters((current) => ({
                ...current,
                scope: event.target.value as SearchFilters['scope'],
              }))
            }
            className="border-border bg-card h-11 border-2 px-2 text-xs font-black"
          >
            <option value="ALL">전체</option>
            <option value="OWNED">내 것만</option>
            <option value="SHARED">공유받은 것만</option>
          </select>
          <select
            aria-label="정렬"
            value={filters.sort}
            onChange={(event) =>
              setFilters((current) => ({
                ...current,
                sort: event.target.value as SearchFilters['sort'],
              }))
            }
            className="border-border bg-card h-11 border-2 px-2 text-xs font-black"
          >
            {sortOptions.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <label className="flex h-11 items-center gap-2 px-2 text-xs font-bold">
            <input
              type="checkbox"
              className="size-4"
              checked={filters.hasImage === true}
              onChange={(event) =>
                setFilters((current) => ({
                  ...current,
                  hasImage: event.target.checked ? true : null,
                }))
              }
            />
            사진 있음
          </label>
        </div>
      </section>

      {hasSearch ? (
        <section className="flex flex-col gap-3" aria-live="polite">
          <SectionTitle aside={<span>{total}개 재고</span>}>
            {q ? `“${q}” 검색 결과` : '필터 결과'}
          </SectionTitle>
          {results.isLoading ? (
            <p role="status" className="text-xs">
              검색 중…
            </p>
          ) : results.isError ? (
            <Alert variant="error">검색하지 못했습니다. 잠시 후 다시 시도해 주세요.</Alert>
          ) : groups.length === 0 ? (
            <Card>
              <CardBody className="items-start gap-2">
                <p className="text-sm font-black">검색 결과가 없습니다.</p>
                <p className="text-foreground/70 text-xs">
                  다른 이름으로 찾거나 필터를 풀어 보세요.
                </p>
                <Button type="button" size="sm" onClick={() => setParams({})}>
                  검색 초기화
                </Button>
              </CardBody>
            </Card>
          ) : (
            <>
              <ol
                aria-label="검색 결과"
                className={
                  groups.length > 1
                    ? 'list-none gap-3 lg:columns-2'
                    : 'flex list-none flex-col gap-3'
                }
              >
                {groups.map((group) => (
                  <li key={group.itemId} className="mb-3 break-inside-avoid">
                    <SearchGroup group={group} onOpen={() => recordView.mutate(group.itemId)} />
                  </li>
                ))}
              </ol>
              {results.hasNextPage ? (
                <Button
                  type="button"
                  variant="secondary"
                  disabled={results.isFetchingNextPage}
                  onClick={() => void results.fetchNextPage()}
                >
                  <ChevronDown aria-hidden="true" className="size-4" />
                  {results.isFetchingNextPage ? '더 불러오는 중…' : '더 보기'}
                </Button>
              ) : null}
            </>
          )}
        </section>
      ) : (
        <section className="grid gap-4 lg:grid-cols-2">
          <Card>
            <CardBody className="gap-3">
              <SectionTitle
                aside={
                  <button
                    type="button"
                    className="text-[11px] font-bold underline"
                    onClick={() => clearRecent.mutate()}
                  >
                    전체 삭제
                  </button>
                }
              >
                최근 검색
              </SectionTitle>
              {recent.data?.items.length ? (
                <div className="flex flex-wrap gap-2">
                  {recent.data.items.map((item) => (
                    <button
                      key={item.id}
                      type="button"
                      onClick={() => submit(item.query)}
                      className="border-border bg-card h-11 border-2 px-3 text-xs font-bold"
                    >
                      {item.query}
                    </button>
                  ))}
                </div>
              ) : (
                <p className="text-foreground/60 text-xs">아직 검색 기록이 없습니다.</p>
              )}
            </CardBody>
          </Card>
          <Card>
            <CardBody className="gap-3">
              <SectionTitle>자주 본 물건</SectionTitle>
              {frequent.data?.items.length ? (
                <div className="flex flex-col gap-2">
                  {frequent.data.items.map((item) => (
                    <Link
                      key={item.itemId}
                      to={`/items/${item.itemId}`}
                      className="hover:bg-sunken flex items-center justify-between gap-2 text-xs font-bold"
                      onClick={() => recordView.mutate(item.itemId)}
                    >
                      <span>{item.itemName}</span>
                      <Badge>{item.viewCount}회</Badge>
                    </Link>
                  ))}
                </div>
              ) : (
                <p className="text-foreground/60 text-xs">물건을 열어 보면 여기에 모입니다.</p>
              )}
            </CardBody>
          </Card>
          <Card className="lg:col-span-2">
            <CardBody className="items-start gap-2">
              <p className="text-sm font-black">검색 팁</p>
              <p className="text-foreground/70 text-xs">
                위치 이름으로도 찾을 수 있습니다. 예: “거실”, “공구함”, “2번 상자”.
              </p>
            </CardBody>
          </Card>
        </section>
      )}

      {results.isFetching && !results.isLoading ? (
        <p role="status" className="text-xs">
          검색 결과를 갱신하는 중…
        </p>
      ) : null}
    </div>
  )
}

function SearchGroup({ group, onOpen }: { group: SearchItemGroup; onOpen: () => void }) {
  return (
    <Card>
      <CardBody className="gap-3">
        <div className="flex items-start justify-between gap-3">
          <div className="flex min-w-0 flex-col gap-1">
            <Link
              to={`/items/${group.itemId}`}
              viewTransition
              onClick={onOpen}
              className="truncate text-base font-black underline-offset-2 hover:underline"
            >
              {group.itemName}
            </Link>
            <div className="flex flex-wrap items-center gap-1">
              {group.effectiveCategory ? <Badge>{group.effectiveCategory.name}</Badge> : null}
              <Badge>
                <Package aria-hidden="true" className="mr-1 size-3" />
                재고 {group.inventoryCount}개
              </Badge>
              {group.hasMoreInventories ? <Badge>일부 표시</Badge> : null}
            </div>
          </div>
        </div>
        <div className="flex flex-col divide-y-2">
          {group.inventories.map((inventory) => (
            <Link
              key={inventory.inventoryId}
              to={`/inventory/${inventory.inventoryId}`}
              viewTransition
              className="hover:bg-sunken flex flex-col gap-1 py-2 first:pt-0 last:pb-0"
            >
              <div className="flex flex-wrap items-center gap-2">
                <span className="text-sm font-bold">{inventory.effectiveName}</span>
                <Badge variant={inventory.status === 'DISPOSED' ? 'dis' : 'neutral'}>
                  {inventory.status === 'IN_USE'
                    ? '사용 중'
                    : inventory.status === 'STORED'
                      ? '보관 중'
                      : '폐기'}
                </Badge>
                {inventory.shared ? <Badge>공유</Badge> : null}
              </div>
              <span className="text-foreground/60 flex items-center gap-1 text-[11px]">
                <MapPin aria-hidden="true" className="size-3" />
                {inventory.locationPath || '위치 없음'}
                {inventory.ownerName ? ` · ${inventory.ownerName}` : ''}
              </span>
            </Link>
          ))}
        </div>
        {group.hasMoreInventories ? (
          <Link
            to={`/items/${group.itemId}`}
            onClick={onOpen}
            className="text-xs font-black underline underline-offset-2"
          >
            재고 {group.inventoryCount}개 모두 보기
          </Link>
        ) : null}
      </CardBody>
    </Card>
  )
}
