import { useCallback, useMemo, useState, type ReactNode } from 'react'
import { AddInventoryContext, type AddInventoryTarget } from './add-inventory-context'
import { AddInventorySheet } from './add-inventory-sheet'

export function AddInventoryProvider({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false)
  const [context, setContext] = useState<AddInventoryTarget>({})

  const openAdd = useCallback((next: AddInventoryTarget = {}) => {
    setContext(next)
    setOpen(true)
  }, [])

  const value = useMemo(() => ({ openAdd }), [openAdd])

  return (
    <AddInventoryContext.Provider value={value}>
      {children}
      {open ? <AddInventorySheet onClose={() => setOpen(false)} context={context} /> : null}
    </AddInventoryContext.Provider>
  )
}
