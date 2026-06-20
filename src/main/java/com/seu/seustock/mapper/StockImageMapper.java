package com.seu.seustock.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StockImageMapper {
  void unsetPrimaryByStockId(Long stockId);

  void insertStockImage(
      @Param("stockId") Long stockId,
      @Param("imageId") Long imageId,
      @Param("displayOrder") int displayOrder,
      @Param("primary") boolean primary);
}
