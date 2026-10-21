import { useQuery } from '@tanstack/react-query'
import type { InventoryLocation } from './api'
import { hierarchyApi } from '@/features/hierarchy/api'

export function useLocationPath(location: InventoryLocation | undefined) {
  const roomQuery = useQuery({
    queryKey: ['hierarchy', 'room', location?.roomId],
    queryFn: () => hierarchyApi.getRoom(location?.roomId ?? ''),
    enabled: Boolean(location?.roomId),
  })
  const shelfQuery = useQuery({
    queryKey: ['hierarchy', 'shelf', location?.shelfId],
    queryFn: () => hierarchyApi.getShelf(location?.shelfId ?? ''),
    enabled: Boolean(location?.shelfId),
  })
  const boxQuery = useQuery({
    queryKey: ['hierarchy', 'box', location?.boxId],
    queryFn: () => hierarchyApi.getBox(location?.boxId ?? ''),
    enabled: Boolean(location?.boxId),
  })

  const names = [roomQuery.data?.name, shelfQuery.data?.name, boxQuery.data?.name].filter(
    Boolean,
  ) as string[]

  return {
    path: names.length > 0 ? names.join(' › ') : (location?.name ?? '위치 없음'),
    isLoading: roomQuery.isLoading || shelfQuery.isLoading || boxQuery.isLoading,
  }
}
