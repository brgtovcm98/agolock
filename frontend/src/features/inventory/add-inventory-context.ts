import { createContext, useContext } from 'react'

export type AddInventoryTarget = {
  roomId?: string
  shelfId?: string
  boxId?: string
  itemId?: string
  source?: 'owned' | 'shared'
}

export type AddInventoryContextValue = {
  openAdd: (target?: AddInventoryTarget) => void
}

export const AddInventoryContext = createContext<AddInventoryContextValue | null>(null)

export function useAddInventory() {
  const value = useContext(AddInventoryContext)
  if (!value) throw new Error('useAddInventory는 AddInventoryProvider 안에서 사용해야 합니다.')
  return value
}
