import { ImageGallery } from './image-gallery'

export function InventoryImages({
  inventoryId,
  canUpload = true,
}: {
  inventoryId: string
  canUpload?: boolean
}) {
  return (
    <ImageGallery
      targetType="INVENTORY"
      targetId={inventoryId}
      canUpload={canUpload}
      relatedQueryKey={['inventory']}
    />
  )
}
