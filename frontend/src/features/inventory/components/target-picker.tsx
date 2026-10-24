import { useInfiniteQuery } from '@tanstack/react-query'
import { useState } from 'react'
import { inventoryApi, wireLocation, type LocationInput } from '../api'
import { Alert } from '@/shared/ui/alert'
import { Button } from '@/shared/ui/button'
import { Select } from '@/shared/ui/select'
import { TextField } from '@/shared/ui/text-field'

export function TargetPicker({
  itemId,
  inventoryId,
  value,
  onChange,
}: {
  itemId?: string
  inventoryId?: string
  value: LocationInput
  onChange: (value: LocationInput) => void
}) {
  const [q, setQ] = useState('')
  const targets = useInfiniteQuery({
    queryKey: ['inventory', 'targets', itemId, inventoryId, q],
    queryFn: ({ pageParam }) =>
      inventoryId
        ? inventoryApi.moveTargets(inventoryId, { q, limit: 100, cursor: pageParam })
        : inventoryApi.inventoryTargets(itemId!, { q, limit: 100, cursor: pageParam }),
    enabled: Boolean(itemId || inventoryId),
    initialPageParam: undefined as string | undefined,
    getNextPageParam: (page) => page.nextCursor ?? undefined,
  })
  const options = targets.data?.pages.flatMap((page) => page.items) ?? []
  const selected = Object.values(wireLocation(value))[0] ?? ''
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="text-xs font-black">위치</legend>
      <TextField label="위치 검색" value={q} onChange={(event) => setQ(event.target.value)} />
      <Select
        aria-label="대상 위치"
        value={selected}
        disabled={!itemId && !inventoryId}
        onChange={(event) => {
          const target = options.find((option) => option.id === event.target.value)
          onChange(
            target
              ? {
                  [target.type === 'ROOM'
                    ? 'roomId'
                    : target.type === 'SHELF'
                      ? 'shelfId'
                      : 'boxId']: target.id,
                }
              : {},
          )
        }}
      >
        <option value="">선택</option>
        {selected && !options.some((option) => option.id === selected) ? (
          <option value={selected}>선택한 위치</option>
        ) : null}
        {options.map((option) => (
          <option key={`${option.type}:${option.id}`} value={option.id}>
            {option.path}
          </option>
        ))}
      </Select>
      {targets.isError ? <Alert variant="error">위치를 불러오지 못했습니다.</Alert> : null}
      {targets.hasNextPage ? (
        <Button
          type="button"
          variant="secondary"
          disabled={targets.isFetchingNextPage}
          onClick={() => void targets.fetchNextPage()}
        >
          위치 더 보기
        </Button>
      ) : null}
    </fieldset>
  )
}
