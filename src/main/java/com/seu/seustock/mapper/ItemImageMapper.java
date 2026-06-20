package com.seu.seustock.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ItemImageMapper {
  void unsetPrimaryByItemId(Long itemId);

  int countByItemIdAndImageId(@Param("itemId") Long itemId, @Param("imageId") Long imageId);

  void updateItemImage(
      @Param("itemId") Long itemId,
      @Param("imageId") Long imageId,
      @Param("displayOrder") int displayOrder,
      @Param("primary") boolean primary);

  void insertItemImage(
      @Param("itemId") Long itemId,
      @Param("imageId") Long imageId,
      @Param("displayOrder") int displayOrder,
      @Param("primary") boolean primary);
}
