import { ImageGallery } from './image-gallery'

export function ItemImages({ itemId, canUpload = false }: { itemId: string; canUpload?: boolean }) {
  return (
    <ImageGallery
      targetType="ITEM"
      targetId={itemId}
      canUpload={canUpload}
      relatedQueryKey={['item']}
    />
  )
}
